package io.groo.damda.namu.service;

import io.groo.damda.namu.client.NamuApiClient;
import io.groo.damda.namu.client.NamuRateLimitException;
import io.groo.damda.namu.client.dto.NamuListEnvelope;
import io.groo.damda.namu.client.dto.NamuLooseEnvelope;
import io.groo.damda.namu.client.dto.NamuQuoteEnvelope;
import io.groo.damda.namu.client.dto.NamuEnvelope;
import io.groo.damda.namu.dto.NamuQuoteDto;
import io.groo.damda.securities.config.NamuProperties;
import io.groo.damda.securities.service.dto.InvestorTradingDay;
import io.groo.damda.stock.domain.StockMaster;
import io.groo.damda.stock.service.StockMasterResolver;
import io.groo.damda.stock.type.StockMarket;
import io.groo.damda.stock.type.StockSecurityType;
import io.groo.damda.toss.dto.TossMarketDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link NamuMarketQueryService} 구현.
 *
 * <p><b>모든 칸 null 허용</b> — 문서에만 있는 칸은 실제 응답에서 비어 올 수 있다(사용자 기준 "문서대로
 * 구현하고 나중에 테스트하며 고친다"). 상류 Output 은 {@code Map<String,String>} 으로 받아 칸 하나가
 * 빠져도 역직렬화가 깨지지 않게 하고, 값 없는 칸은 null 로 내린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamuMarketQueryServiceImpl implements NamuMarketQueryService {

    private static final String KR_PRICE_PATH = "/krstock/quote/v1/currentPrice";
    private static final String KR_ETF_PATH = "/krstock/quote/v1/etfCurrent";
    private static final String GB_PRICE_PATH = "/gbstock/quote/v1/current";
    private static final String KR_PERIOD_PATH = "/krstock/quote/v1/period";
    private static final String KR_EXECUTION_PATH = "/krstock/quote/v1/currentExecution";
    private static final String GB_EXECUTION_PATH = "/gbstock/quote/v1/executionTrend";
    private static final String KR_INVESTOR_PATH = "/krstock/quote/v1/currentInvestor";
    private static final String KR_ETF_COMPONENTS_PATH = "/krstock/quote/v1/etfComponents";
    private static final String KR_AFTER_HOURS_PATH = "/krstock/quote/v1/afterHoursCurrent";
    private static final String GB_MARGIN_PATH = "/gbstock/inquiry/v1/margin";

    /** 상세는 UNT — VI 발동 칸({@code Output_2.vi_type_code})이 UNT 조회 때만 온다. */
    private static final String MARKET_UNT = "UNT";
    private static final String MARKET_KRX = "KRX";
    private static final String KRW = "KRW";
    private static final String USD = "USD";

    private static final int DEFAULT_EXECUTION_COUNT = 20;
    private static final int MAX_EXECUTION_COUNT = 50;
    private static final int DEFAULT_INVESTOR_COUNT = 5;
    private static final int MAX_INVESTOR_COUNT = 100;
    private static final int ORDERBOOK_LEVELS = 10;

    /** 문서가 억원 단위라 한 시가총액을 원으로 바꿀 때 곱한다. */
    private static final BigDecimal EOK = BigDecimal.valueOf(100_000_000L);

    /** EPS 는 하루에 한 번 바뀌는 값이라 6시간(설정) — 실패는 이 시간만큼 기억한다(재시도 폭주 방지). */
    private static final long EPS_FAILURE_TTL_MILLIS = 60_000L;

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private static final ParameterizedTypeReference<
        NamuQuoteEnvelope<Map<String, String>, Map<String, String>, Map<String, String>>> QUOTE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuLooseEnvelope> LOOSE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuQuoteDto.KrExecutionRow>> KR_EXEC_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuQuoteDto.GbExecutionRow>> GB_EXEC_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuQuoteDto.InvestorRow>> INVESTOR_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuQuoteDto.EtfComponentRow>> COMPONENT_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuEnvelope<NamuQuoteDto.AfterHoursRow>> AFTER_HOURS_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuQuoteDto.MarginRow>> MARGIN_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final NamuApiClient namuApiClient;
    private final StockMasterResolver stockMasterResolver;
    private final NamuProperties namuProperties;
    private final NamuQueryService namuQueryService;
    private final Clock clock;

    /** 상세 캐시 — 키 {@code userRowId:KR|GB:symbol}. 사용자끼리 나누지 않는다. */
    private final Map<String, Cached<NamuQuoteDto.Detail>> detailCache = new ConcurrentHashMap<>();
    /** 국내 EPS 캐시 — 키 {@code userRowId:symbol}. 값이 null 이면 "못 구했다" 를 기억한다. */
    private final Map<String, Cached<String>> epsCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<NamuQuoteDto.AfterHours>> afterHoursCache = new ConcurrentHashMap<>();

    private record Cached<T>(T value, long expiresAtMillis) {
        boolean isFresh(long now) {
            return now < expiresAtMillis;
        }
    }

    // === 상세 ===

    @Override
    public NamuQuoteDto.Detail getKrQuote(Long userRowId, String symbol) {
        String key = userRowId + ":KR:" + symbol;
        NamuQuoteDto.Detail hit = freshDetail(key);
        if (hit != null) {
            return hit;
        }
        StockMaster master = master(symbol);
        NamuQuoteDto.Detail detail = master != null && master.getSecurityType() == StockSecurityType.ETF
            ? krEtfDetail(userRowId, symbol)
            : krStockDetail(userRowId, symbol);
        putDetail(key, detail);
        return detail;
    }

    @Override
    public NamuQuoteDto.Detail getGbQuote(Long userRowId, String symbol) {
        String key = userRowId + ":GB:" + symbol;
        NamuQuoteDto.Detail hit = freshDetail(key);
        if (hit != null) {
            return hit;
        }
        Map<String, String> m = namuApiClient.exchange(userRowId, GB_PRICE_PATH,
            Map.of("iem_cd", symbol), QUOTE_TYPE).output0();
        String currency = orDefault(NamuMarketCodes.get(m, "currency_unit"), USD);
        String price = NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "trdprc"));
        String marketPeriod = NamuMarketCodes.get(m, "marketperiod_cls");
        String session = NamuMarketCodes.gbExtendedSession(marketPeriod);
        String afterPrice = NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "pf_trdprc"));
        NamuQuoteDto.ExtendedSession extended = session != null && afterPrice != null
            ? new NamuQuoteDto.ExtendedSession(session, afterPrice,
                NamuMarketCodes.get(m, "pf_pctchng"), NamuMarketCodes.get(m, "pf_netchng_cls"))
            : null;

        NamuQuoteDto.Detail detail = new NamuQuoteDto.Detail(
            orDefault(NamuMarketCodes.get(m, "iem_cd"), symbol),
            NamuMarketCodes.get(m, "iem_nm"),
            currency,
            price,
            previousClose(price, NamuMarketCodes.get(m, "base_prc"),
                NamuMarketCodes.get(m, "netchng"), NamuMarketCodes.get(m, "netchng_cls")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "open_prc")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "high")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "low")),
            NamuMarketCodes.get(m, "acvol"),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "uplimit")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "lolimit")),
            orderbook(m, "best_ask", "best_asiz", "best_bid", "best_bsiz", currency,
                isoTime(zoneOf("US"), NamuMarketCodes.get(m, "quote_time"))),
            List.of(),
            NamuMarketCodes.gbMarketState(marketPeriod),
            NamuMarketCodes.get(m, "industry_name"),
            NamuMarketCodes.get(m, "list_amt"),
            null,
            NamuMarketCodes.get(m, "list_num"),
            NamuMarketCodes.get(m, "per_prc"),
            null,
            NamuMarketCodes.get(m, "eps_prc"),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "w52high_prc")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "w52low_prc")),
            null,
            extended,
            null);
        putDetail(key, detail);
        return detail;
    }

    private NamuQuoteDto.Detail krStockDetail(Long userRowId, String symbol) {
        var res = namuApiClient.exchange(userRowId, KR_PRICE_PATH,
            Map.of("market_cd", MARKET_UNT, "iem_cd", symbol), QUOTE_TYPE);
        Map<String, String> m = res.output0();
        String price = NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_prpr"));
        // EPS 는 currentPrice 다음에 이어서(한 요청 안 직렬) — 한꺼번에 몰리지 않게.
        String eps = krEps(userRowId, symbol);

        return new NamuQuoteDto.Detail(
            orDefault(NamuMarketCodes.get(m, "iem_cd"), symbol),
            NamuMarketCodes.get(m, "iem_nm"),
            KRW,
            price,
            previousClose(price, NamuMarketCodes.get(m, "stck_prdy_clpr"),
                NamuMarketCodes.get(m, "prdy_vrss"), NamuMarketCodes.get(m, "prdy_vrss_sign")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_oprc")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_hgpr")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_lwpr")),
            NamuMarketCodes.get(m, "acml_vol"),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_mxpr")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_llam")),
            orderbook(m, "askp", "askp_rsqn", "bidp", "bidp_rsqn", KRW,
                isoTime(zoneOf("KR"), NamuMarketCodes.get(m, "hoga_bsop_hour"))),
            NamuMarketCodes.krWarnings(m, res.output2()),
            NamuMarketCodes.krMarketState(NamuMarketCodes.get(m, "market_status")),
            NamuMarketCodes.get(m, "bstp_kor_isnm"),
            marketCapWon(NamuMarketCodes.get(m, "hts_avls")),
            NamuMarketCodes.get(m, "stck_lstn_date"),
            NamuMarketCodes.get(m, "lstn_stcn_unit0"),
            NamuMarketCodes.get(m, "per"),
            NamuMarketCodes.get(m, "pbr"),
            eps,
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "w52_hgpr")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "w52_lwpr")),
            NamuMarketCodes.get(m, "for_rate"),
            null,
            null);
    }

    /** ETF 는 {@code etfCurrent} 하나로 현재가 · 시고저 · 상하한가 · 호가 · NAV 를 받는다. */
    private NamuQuoteDto.Detail krEtfDetail(Long userRowId, String symbol) {
        var res = namuApiClient.exchange(userRowId, KR_ETF_PATH, Map.of("iem_cd", symbol), QUOTE_TYPE);
        Map<String, String> m = res.output0();
        Map<String, String> nav = res.output3();
        String price = NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_prpr"));

        NamuQuoteDto.Etf etf = nav == null ? null : new NamuQuoteDto.Etf(
            NamuMarketCodes.get(nav, "itmt_last_nav"),
            NamuMarketCodes.get(nav, "nav_vrss"),
            NamuMarketCodes.get(nav, "nav_sign"),
            NamuMarketCodes.get(nav, "dprt"),
            NamuMarketCodes.get(nav, "dprt_sign"),
            NamuMarketCodes.get(nav, "trc_errt"),
            NamuMarketCodes.get(nav, "totvalue"),
            NamuMarketCodes.get(nav, "cnfg_cnt"));

        return new NamuQuoteDto.Detail(
            orDefault(NamuMarketCodes.get(m, "iem_cd"), symbol),
            NamuMarketCodes.get(m, "iem_nm"),
            KRW,
            price,
            previousClose(price, NamuMarketCodes.get(m, "prdy_clpr"),
                NamuMarketCodes.get(m, "prdy_vrss"), NamuMarketCodes.get(m, "prdy_vrss_sign")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_oprc")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_hgpr")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_lwpr")),
            NamuMarketCodes.get(m, "acml_vol"),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_mxpr")),
            NamuMarketCodes.nonZero(NamuMarketCodes.get(m, "stck_llam")),
            orderbook(m, "askp", "askp_rsqn", "bidp", "bidp_rsqn", KRW,
                isoTime(zoneOf("KR"), NamuMarketCodes.get(m, "bsop_hour"))),
            List.of(),
            null,
            NamuMarketCodes.get(m, "bstp_kor_isnm"),
            marketCapWon(NamuMarketCodes.get(m, "hts_avls")),
            NamuMarketCodes.get(m, "lstn_date"),
            NamuMarketCodes.get(m, "lstn_stcn"),
            null, null, null, null, null,
            NamuMarketCodes.get(m, "for_rate"),
            null,
            etf);
    }

    /**
     * 국내 EPS — {@code period}(gubun 1 · 1건) 머리의 {@code eps}. {@code Output_0} 은 문서가 "명세는 배열 ·
     * 예시는 객체" 라 경고한 자리라 {@code Object} 로 받아 Map 이면 그대로, List 면 첫 원소를 쓴다.
     * 실패 · 없음 → null(상세 전체를 실패시키지 않는다).
     */
    private String krEps(Long userRowId, String symbol) {
        String key = userRowId + ":" + symbol;
        long now = System.currentTimeMillis();
        Cached<String> hit = epsCache.get(key);
        if (hit != null && hit.isFresh(now)) {
            return hit.value();
        }
        String eps = null;
        long ttl = EPS_FAILURE_TTL_MILLIS;
        try {
            Map<String, String> input = new LinkedHashMap<>();
            input.put("market_cd", MARKET_KRX);
            input.put("iem_cd", symbol);
            input.put("edate", YMD.format(LocalDate.now(clock.withZone(zoneOf("KR")))));
            input.put("array_cnt", "1");
            input.put("gubun", "1");
            input.put("today_cls_code", "0");
            NamuLooseEnvelope res = namuApiClient.exchange(userRowId, KR_PERIOD_PATH, input, LOOSE_TYPE);
            eps = epsOf(res.output0());
            ttl = eps == null ? EPS_FAILURE_TTL_MILLIS : namuProperties.getEpsCacheTtlSeconds() * 1000L;
        } catch (NamuRateLimitException e) {
            log.warn("나무 EPS 조회 건너뜀 - 유량 제한(429): userRowId={}", userRowId);
        } catch (RuntimeException e) {
            log.warn("나무 EPS 조회 실패 - 상세는 EPS 없이 내린다: userRowId={}, symbol={}: {}",
                userRowId, symbol, e.getMessage());
        }
        epsCache.put(key, new Cached<>(eps, System.currentTimeMillis() + ttl));
        return eps;
    }

    private static String epsOf(Object output0) {
        Object head = output0 instanceof List<?> list ? (list.isEmpty() ? null : list.get(0)) : output0;
        if (head instanceof Map<?, ?> map) {
            Object eps = map.get("eps");
            return eps == null ? null : NamuMarketCodes.blankToNull(String.valueOf(eps));
        }
        return null;
    }

    private NamuQuoteDto.Detail freshDetail(String key) {
        Cached<NamuQuoteDto.Detail> hit = detailCache.get(key);
        return hit != null && hit.isFresh(System.currentTimeMillis()) ? hit.value() : null;
    }

    private void putDetail(String key, NamuQuoteDto.Detail detail) {
        long ttl = namuProperties.getQuoteCacheTtlSeconds() * 1000L;
        if (ttl > 0) {
            evictExpired(detailCache);
            detailCache.put(key, new Cached<>(detail, System.currentTimeMillis() + ttl));
        }
    }

    /** 키가 (사용자 × 종목)이라 유계지만, 사용자가 종목을 훑으면 늘기만 하므로 넘치면 만료분을 걷는다. */
    private static <T> void evictExpired(Map<String, Cached<T>> cache) {
        if (cache.size() >= 2_000) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(e -> !e.getValue().isFresh(now));
        }
    }

    // === 체결 ===

    @Override
    public List<TossMarketDto.Trade> getKrExecutions(Long userRowId, String symbol, Integer count) {
        int n = clamp(count, DEFAULT_EXECUTION_COUNT, MAX_EXECUTION_COUNT);
        Map<String, String> input = new LinkedHashMap<>();
        input.put("market_cd", MARKET_UNT);
        input.put("iem_cd", symbol);
        input.put("array_cnt", String.valueOf(n));
        input.put("view_main_yn", "N");
        List<NamuQuoteDto.KrExecutionRow> rows = namuApiClient
            .exchange(userRowId, KR_EXECUTION_PATH, input, KR_EXEC_TYPE).resultOrEmpty();
        ZoneId zone = zoneOf("KR");
        return rows.stream()
            .map(r -> new TossMarketDto.Trade(NamuMarketCodes.nonZero(r.price()),
                NamuMarketCodes.blankToNull(r.volume()), isoTime(zone, r.time()), KRW))
            .sorted(NEWEST_FIRST)
            .limit(n)
            .toList();
    }

    @Override
    public List<TossMarketDto.Trade> getGbExecutions(Long userRowId, String symbol, Integer count) {
        int n = clamp(count, DEFAULT_EXECUTION_COUNT, MAX_EXECUTION_COUNT);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("period_type", "1");
        input.put("req_cnt", n);
        input.put("iem_cd", symbol);
        List<NamuQuoteDto.GbExecutionRow> rows = namuApiClient
            .exchange(userRowId, GB_EXECUTION_PATH, input, GB_EXEC_TYPE).resultOrEmpty();
        StockMaster master = master(symbol);
        // 문서에 시간대가 없다 — 캔들과 같은 가정(거래소 현지 시각).
        ZoneId zone = master == null ? zoneOf("US") : zoneOf(master.getCountryCode());
        String currency = master == null || master.getCurrency() == null || master.getCurrency().isBlank()
            ? USD : master.getCurrency();
        return rows.stream()
            .map(r -> new TossMarketDto.Trade(NamuMarketCodes.nonZero(r.price()),
                NamuMarketCodes.blankToNull(r.volume()), isoDateTime(zone, r.date(), r.time()), currency))
            .sorted(NEWEST_FIRST)
            .limit(n)
            .toList();
    }

    private static final Comparator<TossMarketDto.Trade> NEWEST_FIRST =
        Comparator.comparing(TossMarketDto.Trade::timestamp, Comparator.nullsLast(Comparator.reverseOrder()));

    // === 투자자별 ===

    @Override
    public List<InvestorTradingDay> getKrInvestors(Long userRowId, String symbol, Integer count) {
        StockMaster master = master(symbol);
        if (master != null && !"KR".equals(master.getCountryCode())) {
            return List.of();
        }
        int n = clamp(count, DEFAULT_INVESTOR_COUNT, MAX_INVESTOR_COUNT);
        Map<String, String> input = new LinkedHashMap<>();
        input.put("market_cd", MARKET_KRX);
        input.put("iem_cd", symbol);
        input.put("array_cnt", String.valueOf(n));
        return namuApiClient.exchange(userRowId, KR_INVESTOR_PATH, input, INVESTOR_TYPE).resultOrEmpty().stream()
            .map(r -> new InvestorTradingDay(isoDate(r.date()), NamuMarketCodes.blankToNull(r.individual()),
                NamuMarketCodes.blankToNull(r.foreigner()), NamuMarketCodes.blankToNull(r.institution())))
            .filter(d -> d.date() != null)
            .sorted(Comparator.comparing(InvestorTradingDay::date).reversed())
            .limit(n)
            .toList();
    }

    // === ETF · 시간외 ===

    @Override
    public List<NamuQuoteDto.EtfComponent> getEtfComponents(Long userRowId, String symbol) {
        return namuApiClient.exchange(userRowId, KR_ETF_COMPONENTS_PATH, Map.of("iem_cd", symbol), COMPONENT_TYPE)
            .resultOrEmpty().stream()
            .sorted(Comparator.comparing((NamuQuoteDto.EtfComponentRow r) -> NamuQueryServiceImpl.decimal(r.weight()),
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(r -> new NamuQuoteDto.EtfComponent(NamuMarketCodes.blankToNull(r.symbol()),
                NamuMarketCodes.blankToNull(r.name()), NamuMarketCodes.blankToNull(r.weight()),
                NamuMarketCodes.nonZero(r.price()), NamuMarketCodes.blankToNull(r.changeRate()),
                NamuMarketCodes.blankToNull(r.changeSign())))
            .toList();
    }

    @Override
    public NamuQuoteDto.AfterHours getKrAfterHours(Long userRowId, String symbol) {
        String key = userRowId + ":" + symbol;
        Cached<NamuQuoteDto.AfterHours> hit = afterHoursCache.get(key);
        if (hit != null && hit.isFresh(System.currentTimeMillis())) {
            return hit.value();
        }
        NamuQuoteDto.AfterHoursRow row = namuApiClient.postObject(userRowId, KR_AFTER_HOURS_PATH,
            Map.of("iem_cd", symbol), AFTER_HOURS_TYPE);
        String price = row == null ? null : NamuMarketCodes.nonZero(row.price());
        NamuQuoteDto.AfterHours value = price == null ? NamuQuoteDto.AfterHours.empty()
            : new NamuQuoteDto.AfterHours(price, NamuMarketCodes.blankToNull(row.change()),
                NamuMarketCodes.blankToNull(row.changeRate()), NamuMarketCodes.blankToNull(row.changeSign()),
                NamuMarketCodes.blankToNull(row.time()));
        long ttl = namuProperties.getQuoteCacheTtlSeconds() * 1000L;
        if (ttl > 0) {
            evictExpired(afterHoursCache);
            afterHoursCache.put(key, new Cached<>(value, System.currentTimeMillis() + ttl));
        }
        return value;
    }

    // === 증거금 ===

    @Override
    public NamuQuoteDto.Margin getMargin(Long userRowId) {
        String account = namuQueryService.resolveAccountNo(userRowId, null);
        if (account == null) {
            return NamuQuoteDto.Margin.empty();
        }
        return namuApiClient.exchange(userRowId, GB_MARGIN_PATH, Map.of("act_no", account), MARGIN_TYPE)
            .resultOrEmpty().stream()
            .filter(r -> USD.equalsIgnoreCase(r.currency()))
            .findFirst()
            .map(r -> new NamuQuoteDto.Margin(USD, NamuMarketCodes.blankToNull(r.foreignDeposit()),
                NamuMarketCodes.blankToNull(r.foreignWithdrawable()),
                NamuMarketCodes.nonZero(r.baseExchangeRate())))
            .orElseGet(NamuQuoteDto.Margin::empty);
    }

    // === 보조 ===

    private StockMaster master(String symbol) {
        return stockMasterResolver.resolve((StockMarket) null, symbol).orElse(null);
    }

    private static ZoneId zoneOf(String countryCode) {
        return NamuQueryServiceImpl.MARKET_ZONES.getOrDefault(countryCode, NamuQueryServiceImpl.MARKET_ZONES.get("US"));
    }

    private static int clamp(Integer count, int fallback, int max) {
        if (count == null || count <= 0) {
            return fallback;
        }
        return Math.min(count, max);
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /** 문서의 억원 시가총액을 원으로 — 단위가 문서에 없는 칸이라 억으로 읽는다(T: dev 응답으로 확인 대기). */
    private static String marketCapWon(String eok) {
        BigDecimal v = NamuQueryServiceImpl.decimal(eok);
        return v == null || v.signum() <= 0 ? null : v.multiply(EOK).toBigInteger().toString();
    }

    private static String previousClose(String price, String rawPrevious, String rawChange, String sign) {
        BigDecimal p = NamuQueryServiceImpl.decimal(price);
        if (p == null) {
            return NamuMarketCodes.nonZero(rawPrevious);
        }
        BigDecimal prev = NamuQueryServiceImpl.previousClose(p, rawPrevious, rawChange, sign);
        return prev == null ? null : prev.toPlainString();
    }

    /** 호가 10단 — 가격이 비었거나 0 인 단계는 건너뛴다. 단계가 하나도 없으면 null(그 구역을 그리지 않는다). */
    private static TossMarketDto.OrderbookResponse orderbook(Map<String, String> m, String askPrice, String askSize,
                                                             String bidPrice, String bidSize, String currency,
                                                             String timestamp) {
        List<TossMarketDto.OrderbookEntry> asks = levels(m, askPrice, askSize);
        List<TossMarketDto.OrderbookEntry> bids = levels(m, bidPrice, bidSize);
        if (asks.isEmpty() && bids.isEmpty()) {
            return null;
        }
        return new TossMarketDto.OrderbookResponse(timestamp, currency, asks, bids);
    }

    private static List<TossMarketDto.OrderbookEntry> levels(Map<String, String> m, String pricePrefix, String sizePrefix) {
        List<TossMarketDto.OrderbookEntry> out = new ArrayList<>();
        for (int i = 1; i <= ORDERBOOK_LEVELS; i++) {
            String price = NamuMarketCodes.nonZero(NamuMarketCodes.get(m, pricePrefix + i));
            if (price != null) {
                out.add(new TossMarketDto.OrderbookEntry(price, NamuMarketCodes.get(m, sizePrefix + i)));
            }
        }
        return out;
    }

    /** {@code HH:mm:ss} · {@code HHmmss} · {@code HHmm} → 오늘(그 거래소 날짜) 시각의 ISO-8601. 못 읽으면 null. */
    private String isoTime(ZoneId zone, String raw) {
        return isoDateTime(zone, null, raw);
    }

    private String isoDateTime(ZoneId zone, String rawDate, String rawTime) {
        LocalTime time = parseTime(rawTime);
        if (time == null) {
            return null;
        }
        LocalDate date = parseDate(rawDate);
        if (date == null) {
            date = LocalDate.now(clock.withZone(zone));
        }
        return ISO_OFFSET.format(date.atTime(time).atZone(zone));
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        String padded = (digits + "000000").substring(0, 6);
        try {
            return LocalTime.of(Integer.parseInt(padded.substring(0, 2)),
                Integer.parseInt(padded.substring(2, 4)), Integer.parseInt(padded.substring(4, 6)));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YMD);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String isoDate(String raw) {
        LocalDate d = parseDate(raw);
        return d == null ? null : d.toString();
    }

    @SuppressWarnings("unused")
    private static boolean notNull(Object o) {
        return Objects.nonNull(o);
    }
}
