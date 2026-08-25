package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.service.StockMasterResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamuQueryServiceImpl implements NamuQueryService {

    private static final String KR_PRICE_PATH = "/krstock/quote/v1/currentPrice";
    private static final String GB_PRICE_PATH = "/gbstock/quote/v1/current";
    private static final String ACCOUNT_PATH = "/n2/acctinfo";
    private static final String KR_BALANCE_PATH = "/krstock/inquiry/v1/balance";
    private static final String GB_BALANCE_PATH = "/gbstock/inquiry/v1/balance";

    /** 국내 거래소 기본값. NXT 거래 종목도 KRX 로 물으면 정규시장 시세가 온다. */
    private static final String MARKET_KRX = "KRX";
    private static final String KRW = "KRW";

    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.KrPrice>> KR_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.GbPrice>> GB_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuAccountDto.Account>> ACCOUNT_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDto.KrBalanceSummary, NamuAccountDto.KrHolding>> KR_BALANCE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding>> GB_BALANCE_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final NamuApiClient namuApiClient;
    private final StockMasterResolver stockMasterResolver;
    private final NamuProperties namuProperties;

    /**
     * 시세 캐시. 나무엔 다건 시세 API 가 없어 종목마다 1콜이 나가는데, 자산 화면은 10초마다
     * 폴링하고 목록·상세가 각각 조회한다 — 캐시가 없으면 한 사용자가 초당 몇 콜씩 지속적으로
     * 낸다. 인스턴스별 메모리 캐시로 충분하다(읽기 전용이고 TTL 이 짧다).
     */
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    private record CachedQuote(PriceQuote quote, long expiresAtMillis) {
        boolean isFresh(long now) {
            return now < expiresAtMillis;
        }
    }

    @Override
    public PriceQuote getKrPrice(Long userRowId, String symbol, String marketCode) {
        NamuMarketDto.KrPrice p = namuApiClient.postObject(userRowId, KR_PRICE_PATH,
            Map.of("market_cd", marketCode == null || marketCode.isBlank() ? MARKET_KRX : marketCode,
                   "iem_cd", symbol),
            KR_TYPE);
        return p == null ? null : quote(symbol, p.price(), KRW, p.previousClose(), p.change(), p.changeSign());
    }

    @Override
    public PriceQuote getGbPrice(Long userRowId, String symbol) {
        NamuMarketDto.GbPrice p = namuApiClient.postObject(userRowId, GB_PRICE_PATH,
            Map.of("iem_cd", symbol), GB_TYPE);
        return p == null ? null : quote(symbol, p.price(), p.currency(), p.previousClose(), p.change(), p.changeSign());
    }

    /**
     * 국내·해외를 섞어 조회한다.
     *
     * <p>나무는 <b>종목 다건 한 번에</b> 주는 시세 API 가 없어 종목마다 한 콜씩 나간다
     * (토스는 콤마 구분 다건이다). 보유 종목 수만큼 호출이 나가므로 자산 평가 경로에서만 쓰고,
     * 한 종목이 실패해도 나머지는 살린다 — 전체를 접으면 평가가 통째로 멈춘다.
     *
     * <p>국내인지 해외인지는 {@code stock_master} 가 정한다. <b>시장을 알면 그걸로 특정하고</b>,
     * 모르면 {@link StockMasterResolver} 의 우선순위로 하나를 고른다. 예전에는 후보가 둘
     * 이상이면 통째로 건너뛰었는데, NH 소스가 시장을 6개 늘린 뒤로 SPY·IVV 같은 흔한 종목이
     * 거기 걸려 평가에서 조용히 빠졌다.
     */
    @Override
    public List<PriceQuote> getPrices(Long userRowId, List<InstrumentRef> instruments) {
        long now = System.currentTimeMillis();
        long deadline = now + namuProperties.getPriceBatchBudgetMs();
        List<PriceQuote> quotes = new ArrayList<>();
        int fetched = 0;
        int cached = 0;
        int skipped = 0;

        for (InstrumentRef ref : instruments) {
            StockMaster stock = stockMasterResolver.resolve(ref.marketCode(), ref.symbol()).orElse(null);
            if (stock == null) {
                log.debug("나무 시세 건너뜀 - 마스터에 없는 종목: market={}, symbol={}",
                    ref.marketCode(), ref.symbol());
                continue;
            }

            String key = cacheKey(userRowId, stock);
            CachedQuote hit = quoteCache.get(key);
            if (hit != null && hit.isFresh(System.currentTimeMillis())) {
                quotes.add(hit.quote());
                cached++;
                continue;
            }

            // 예산을 넘기면 여기서 멈춘다. 지금까지 받은 건 캐시에 남으므로 다음 폴링이
            // 나머지를 이어 받아 몇 번 안에 다 찬다 — 매번 처음부터 다시 긁지 않는다.
            if (System.currentTimeMillis() >= deadline) {
                skipped++;
                continue;
            }

            try {
                PriceQuote quote = "KR".equals(stock.getCountryCode())
                    ? getKrPrice(userRowId, stock.getSymbol(), MARKET_KRX)
                    : getGbPrice(userRowId, stock.getSymbol());
                fetched++;
                if (quote != null) {
                    quotes.add(quote);
                    quoteCache.put(key, new CachedQuote(quote,
                        System.currentTimeMillis() + namuProperties.getQuoteCacheTtlSeconds() * 1000L));
                }
            } catch (RuntimeException e) {
                log.warn("나무 시세 조회 실패 - symbol={}: {}", ref.symbol(), e.getMessage());
            }
        }

        if (skipped > 0) {
            log.warn("나무 시세 시간 예산 초과 - 나머지는 다음 조회로 미룬다: userRowId={}, 요청={}, "
                    + "캐시={}, 조회={}, 미룸={}",
                userRowId, instruments.size(), cached, fetched, skipped);
        }
        return quotes;
    }

    private static String cacheKey(Long userRowId, StockMaster stock) {
        return userRowId + ":" + stock.getMarketCode().name() + ":" + stock.getSymbol();
    }

    // === 계좌·잔고 ===

    @Override
    public List<NamuAccountDto.Account> getAccounts(Long userRowId) {
        // 입력 파라미터가 없는 조회지만 봉투는 그대로 지킨다 — 서버가 Input_0 을 요구한다.
        // 계좌목록은 우리가 쓰는 것 중 유일하게 Output_0 이 배열이다.
        return namuApiClient.postList(userRowId, ACCOUNT_PATH, Map.of(), ACCOUNT_TYPE);
    }

    @Override
    public NamuAccountDto.Holdings getHoldings(Long userRowId, String accountNo, String currency) {
        String account = accountNo != null && !accountNo.isBlank() ? accountNo : firstAccountNo(userRowId);
        if (account == null) {
            return NamuAccountDto.Holdings.empty("", currency);
        }
        return KRW.equalsIgnoreCase(currency)
            ? krHoldings(userRowId, account)
            : gbHoldings(userRowId, account, currency);
    }

    /**
     * 환율은 <b>종목별 행(Output_1)</b>에 실려 온다 — 계좌 요약이 아니다. 종목마다 통화가
     * 달라 계좌 단위로 환율 하나를 들 수 없는 구조라서다.
     *
     * <p>그래서 제약이 하나 붙는다: <b>해당 통화 보유 종목이 하나도 없으면 환율을 못 구한다.</b>
     * 그때 null 로 접어 외화 평가만 건너뛴다(부분합으로 금액을 왜곡하지 않는 기존 규칙).
     * 조용히 넘어가지 않게 warn 을 남긴다 — 값이 계속 안 잡히면 이 경로를 다시 봐야 한다.
     */
    @Override
    public BigDecimal getFxRate(Long userRowId, String currency) {
        String account = firstAccountNo(userRowId);
        if (account == null) {
            log.debug("나무 환율 조회 불가 - 계좌 없음 (userRowId={})", userRowId);
            return null;
        }
        String want = currency == null || currency.isBlank() ? "USD" : currency;
        try {
            List<NamuAccountDto.GbHolding> items = namuApiClient
                .exchange(userRowId, GB_BALANCE_PATH, gbBalanceInput(account, want), GB_BALANCE_TYPE)
                .items();

            BigDecimal rate = items.stream()
                .filter(h -> want.equalsIgnoreCase(h.currency()))
                .map(h -> decimal(h.baseExchangeRate()))
                .filter(r -> r != null && r.signum() > 0)
                .findFirst()
                .orElse(null);

            if (rate == null) {
                log.warn("나무 환율 미확보 - {} 보유 종목이 없거나 환율 필드가 비었다 (userRowId={}, 종목={}건)",
                    want, userRowId, items.size());
            }
            return rate;
        } catch (RuntimeException e) {
            log.warn("나무 환율 조회 실패 (userRowId={}): {}", userRowId, e.getMessage());
            return null;
        }
    }

    /** 계좌를 안 넘겨받으면 첫 계좌를 쓴다. 계좌가 없으면 null — 호출부가 빈 잔고로 본다. */
    private String firstAccountNo(Long userRowId) {
        List<NamuAccountDto.Account> accounts = getAccounts(userRowId);
        return accounts.isEmpty() ? null : accounts.get(0).accountNo();
    }

    private NamuAccountDto.Holdings krHoldings(Long userRowId, String account) {
        NamuPagedEnvelope<NamuAccountDto.KrBalanceSummary, NamuAccountDto.KrHolding> res =
            namuApiClient.exchange(userRowId, KR_BALANCE_PATH, Map.of(
                "act_no", account,
                "bnc_bse_cd", "5",     // 주식잔고평가(현재가기준) — 화면이 보여줄 평가액이라 체결기준(1)이 아니다
                "ltg_aot_dit_cd", "1", // 상장종목만 (9=전체는 상장폐지분까지 섞인다)
                "aet_bse", "2",        // 총자산
                "qut_dit_cd", "UNT"    // 통합시세 — NXT 거래 종목도 한 번에 잡힌다(KRX/NXT 로 나눠 부를 필요가 없다)
            ), KR_BALANCE_TYPE);

        NamuAccountDto.KrBalanceSummary s = res.summary();
        List<NamuAccountDto.HoldingItem> items = res.items().stream()
            .map(h -> new NamuAccountDto.HoldingItem(h.symbol(), h.name(), h.quantity(),
                h.avgPrice(), h.currentPrice(), h.evalAmount(), h.profitLoss()))
            .toList();

        return new NamuAccountDto.Holdings(account, KRW,
            s == null ? "0" : s.totalEvalAmount(),
            s == null ? "0" : s.totalProfitLoss(),
            s == null ? "0" : s.profitRate(),
            items);
    }

    private NamuAccountDto.Holdings gbHoldings(Long userRowId, String account, String currency) {
        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding> res =
            namuApiClient.exchange(userRowId, GB_BALANCE_PATH, gbBalanceInput(account, currency), GB_BALANCE_TYPE);

        NamuAccountDto.GbBalanceSummary s = res.summary();
        List<NamuAccountDto.HoldingItem> items = res.items().stream()
            // 한글명이 비면 영문명으로 채운다 — 목록에 빈 칸이 남지 않게.
            .map(h -> new NamuAccountDto.HoldingItem(h.symbol(),
                h.name() == null || h.name().isBlank() ? h.nameEn() : h.name(),
                h.quantity(), h.avgPrice(), h.currentPrice(), h.evalAmount(), h.profitLoss()))
            .toList();

        // 요약도 종목과 같은 외화 기준이다 — 섞으면 원화 금액에 USD 를 붙여 보여주게 된다.
        return new NamuAccountDto.Holdings(account, currency,
            s == null ? "0" : s.evalAmountSum(),
            s == null ? "0" : s.profitLossSum(),
            s == null ? "0" : s.profitRate(),
            items);
    }

    /**
     * 해외 잔고 요청.
     *
     * <p>{@code cur_cd} 는 문서상 {@code KRW}=전체(원화 환산) · {@code USD}/{@code CNY}/
     * {@code HKD}/{@code JPY}=해당 통화다. 기본을 KRW 로 둬 <b>보유 전체</b>를 한 번에 받는다.
     *
     * <p>{@code fc_sec_trd_nat_cd}(거래국가)는 문서에 개별 코드(200 미국 · 070 일본 · 120 홍콩 ·
     * 160 상해 · 170 심천)만 있고 <b>"전체" 표기가 없다.</b> 빈 값으로 보내 전체를 노리되,
     * 실제 키로 확인이 필요한 자리다 — 안 먹으면 국가별로 나눠 부르면 된다.
     */
    private static Map<String, String> gbBalanceInput(String account, String currency) {
        return Map.of(
            "act_no", account,
            "qut_iqr_dit_cd", "1",   // 정규장
            "fc_sec_trd_nat_cd", "", // 전체 (문서 미표기 — 실키 확인 필요)
            "cur_cd", currency == null || currency.isBlank() ? KRW : currency,
            "xns_dit_cd", "1"        // 비용 포함 — 실제 손익에 가깝다
        );
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 나무는 시세 응답에 <b>전일 종가를 그대로</b> 준다 — 국내 {@code stck_prdy_clpr},
     * 해외 {@code base_prc}. 그걸 쓴다(토스는 캔들을 따로 받아야 해서 못 준다).
     *
     * <p><b>전일대비로 역산하지 않는다.</b> 전일대비는 절대값 + 부호코드로 오는데
     * 그 코드값 정의가 공개 문서 어디에도 없다. 관례대로 1·2·3 을 상승, 4·5 를 하락으로
     * 찍었다가 틀리면 <b>등락이 통째로 뒤집혀</b> 보인다. 전일 종가가 비어 있을 때만
     * 폴백으로 쓰고, 부호를 모르면 방향을 찍지 않고 비운다.
     */
    private static PriceQuote quote(String symbol, String rawPrice, String currency,
                                    String rawPreviousClose, String rawChange, String changeSign) {
        BigDecimal price = decimal(rawPrice);
        if (price == null) {
            log.warn("나무 시세 파싱 실패: symbol={}, price={}", symbol, rawPrice);
            return null;
        }
        String cur = currency == null || currency.isBlank() ? KRW : currency;
        return new PriceQuote(symbol, price, cur, previousClose(price, rawPreviousClose, rawChange, changeSign));
    }

    private static BigDecimal previousClose(BigDecimal price, String rawPreviousClose,
                                            String rawChange, String changeSign) {
        BigDecimal direct = decimal(rawPreviousClose);
        if (direct != null && direct.signum() > 0) {
            return direct;
        }
        // 폴백 — 응답에 전일 종가가 비어 있을 때만.
        BigDecimal signed = signedChange(rawChange, changeSign);
        return signed == null ? null : price.subtract(signed);
    }

    /**
     * 부호코드로 전일대비의 방향을 정한다.
     *
     * <p><b>이 코드표는 문서에 없다.</b> 한국투자증권 계열 관례(1 상한 · 2 상승 · 3 보합 ·
     * 4 하한 · 5 하락)를 옮긴 가정이라, 전일 종가를 직접 못 받았을 때만 쓴다.
     * 모르는 값이면 null — 방향을 찍느니 등락을 감추는 편이 낫다.
     */
    private static BigDecimal signedChange(String rawChange, String changeSign) {
        BigDecimal change = decimal(rawChange);
        if (change == null || changeSign == null) {
            return null;
        }
        return switch (changeSign.trim()) {
            case "1", "2", "3" -> change.abs();
            case "4", "5" -> change.abs().negate();
            default -> null;
        };
    }
}
