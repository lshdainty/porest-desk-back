package com.porest.desk.namu.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.NamuRateLimitException;
import com.porest.desk.namu.client.dto.NamuCandleEnvelope;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuCandleDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.type.CandleInterval;
import com.porest.desk.securities.type.NamuEnvironment;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.service.dto.SecuritiesCandle;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.service.StockMasterResolver;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final String KR_PERIOD_PATH = "/krstock/quote/v1/period";
    private static final String GB_PERIOD_PATH = "/gbstock/quote/v1/period";

    /**
     * 주기구분({@code gubun}) — <b>국내와 해외가 같은 주기에 다른 숫자를 쓴다.</b>
     *
     * <pre>
     *   국내 /krstock/quote/v1/period : 1.일 2.주 3.월 4.년 5.분 6.초 7.틱
     *   해외 /gbstock/quote/v1/period : 1.틱 2.분 3.일 4.주 5.월
     * </pre>
     *
     * <p>겹치는 숫자가 뜻이 다르다는 게 함정이다 — 해외에 {@code 1} 을 보내면 <b>에러가 아니라
     * 틱 데이터</b>가 오고, 국내에 {@code 3} 을 보내면 월봉이 온다. 둘 다 "차트가 이상하다" 로만
     * 보이고 예외가 안 난다. 그래서 숫자를 코드에 직접 쓰지 않고 이름을 붙여 둔다.
     */
    private static final String KR_GUBUN_DAY = "1";
    private static final String KR_GUBUN_MINUTE = "5";
    private static final String GB_GUBUN_DAY = "3";
    private static final String GB_GUBUN_MINUTE = "2";

    /**
     * 조회단위({@code xtick}) — 스펙이 <b>선언 길이만큼 0 을 채우라</b>고 본다.
     *
     * <p>해외 개별종목은 "주기구분 일인경우 0001"(길이 4), 형제 API 인 지수·환율은
     * "일인경우 001"(길이 3)이라고 명시한다. 국내는 길이 3 만 적혀 있고 예시가 없어
     * 같은 규칙으로 맞춘다({@code 001} = 1분).
     *
     * <p>국내는 "분/초/틱시 입력" 이라 일봉에는 아예 넣지 않는다. 해외는 필수라 항상 넣는다.
     */
    private static final String KR_XTICK_1MIN = "001";
    private static final String GB_XTICK_1UNIT = "0001";

    /** 해외 필수값 {@code maxavg}(최대이평, 길이 3). 이동평균은 화면이 직접 계산하므로 뜻은 없다. */
    private static final String GB_MAXAVG = "020";

    /**
     * 당일조회 구분 — <b>이름이 비슷한데 뜻이 반대다.</b>
     *
     * <pre>
     *   국내 today_cls_code : 0.전체조회      1.당일만조회
     *   해외 today_cls      : 0.종료일조회    1.당일조회
     * </pre>
     *
     * <p>양쪽 다 {@code 0} 을 쓴다 — 우리는 항상 종료일까지 거슬러 읽고 싶기 때문이다.
     * {@code 1} 을 쓰면 주말·휴장일에 "당일" 데이터가 없어 <b>0건이 온다</b>. 한국 사용자가
     * 토요일에 미국 주식 차트를 열면 빈 차트를 보게 되는 자리라, 값을 고정한다.
     */
    private static final String TODAY_CLS_ALL = "0";

    /** 해외 장시간구분 {@code market_cls} — 1.정규장. 프리·애프터를 섞으면 봉이 튄다. */
    private static final String GB_MARKET_REGULAR = "1";

    /** {@code edate}/{@code end_dt} 와 커서에 쓰는 {@code YYYYMMDD}. */
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    /** 캔들 {@code timestamp} 형식. 오프셋을 반드시 실어 보낸다 — {@link SecuritiesCandle} 참고. */
    private static final DateTimeFormatter CANDLE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 거래소 현지 타임존. 캔들 시각은 <b>거래소 시계</b>로 오므로 여기서 오프셋을 붙여야
     * 받는 쪽이 자기 타임존으로 옳게 환산한다.
     *
     * <p>{@code StockMarket} 이 드는 국가코드 전부를 덮는다. 모르는 국가는 미국으로 본다 —
     * 나무 해외 연동이 미국 고정({@link #NATION_US})이라 실제로 들어올 수 있는 건 그쪽뿐이다.
     */
    private static final Map<String, ZoneId> MARKET_ZONES = Map.of(
        "KR", ZoneId.of("Asia/Seoul"),
        "US", ZoneId.of("America/New_York"),
        "JP", ZoneId.of("Asia/Tokyo"),
        "HK", ZoneId.of("Asia/Hong_Kong"),
        "CN", ZoneId.of("Asia/Shanghai"),
        "VN", ZoneId.of("Asia/Ho_Chi_Minh"),
        "AU", ZoneId.of("Australia/Sydney"),
        "DE", ZoneId.of("Europe/Berlin"),
        "GB", ZoneId.of("Europe/London"),
        "ID", ZoneId.of("Asia/Jakarta"));

    private static final ZoneId DEFAULT_ZONE = MARKET_ZONES.get("US");

    /** 국가코드 {@code KR} 이면 국내 엔드포인트. 그 밖은 전부 해외다. */
    private static final String COUNTRY_KR = "KR";


    /** 국내 거래소 기본값. NXT 거래 종목도 KRX 로 물으면 정규시장 시세가 온다. */
    private static final String MARKET_KRX = "KRX";

    /**
     * 나무 국내 시세의 {@code market_cd} 허용값 — <b>나무 어휘다.</b>
     *
     * <p>같은 이름의 {@code StockMarket.marketCode} 는 {@code KOSPI}·{@code NAS} 같은
     * <b>다른 어휘</b>다. 두 어휘가 이 클래스 안에서 나란히 쓰인다 —
     * {@link #getKrPrice} 의 파라미터는 여기 값이고, {@link #getPrices} 의
     * {@code ref.marketCode()} 는 저쪽 값이다.
     *
     * <p>그래서 화이트리스트를 둔다. 검증이 없으면 {@code KOSPI} 를 태웠을 때 나무가
     * 모르는 값이라 그 종목만 응답이 비고, 화면에는 예외 없이 '—' 만 뜬다. 조용히 틀리느니
     * 400 으로 시끄럽게 틀리는 게 낫다.
     */
    private static final Set<String> KR_MARKETS = Set.of(MARKET_KRX, "NXT", "UNT");

    private static final String KRW = "KRW";

    /**
     * 해외 잔고의 거래국가 — <b>미국 고정</b>이다.
     *
     * <p><b>여기 빈 문자열을 넣으면 안 된다.</b> 예전 값이 {@code ""} 였다. "전체" 를 노린
     * 것이었는데 나무 스펙에 그런 코드값이 없다 — {@code /gbstock/inquiry/v1/balance} 의
     * {@code fc_sec_trd_nat_cd} 는 <b>필수</b>이고 개별 국가(200 미국 · 070 일본 · 120 홍콩 ·
     * 160 상해 · 170 심천)만 받는다. 나무는 이걸 <b>에러로 거절하지 않고 0건을 돌려줬다.</b>
     * 그래서 화면에는 "보유 중인 종목이 없어요" 가 떴고, 실제로는 미국 주식을 들고 있었다
     * (dev 실측 2026-08-26: {@code currency=USD} → {@code items:[] totalEvalAmount:"0.000"}).
     * 국내 잔고({@code /krstock/inquiry/v1/balance})에는 이 파라미터가 없어 멀쩡했다.
     *
     * <p><b>왜 미국만인가</b> — 사용자가 미국만 쓴다고 정했다(2026-08-26). 국가를 하나로
     * 박으면 호출도 한 번으로 끝난다.
     *
     * <p><b>다른 국가를 붙이려면</b> 국가별로 <b>나눠 호출해 병합</b>해야 한다. 나무엔 여러
     * 국가를 한 번에 주는 잔고 API 가 없고, 호출 수가 국가 수만큼 늘어 <b>429(한도 초과)</b> 를
     * 건드린다({@code NamuApiClient} 는 429 를 재시도하지 않는다). 합칠 때 요약({@code Output_0})은
     * 통화가 섞여 그대로 더할 수 없다는 것도 같이 풀어야 한다.
     * 참고로 형제 API {@code /gbstock/inquiry/v1/periodPnl} 에는 {@code 000.전체} 가 있다 —
     * 잔고에도 먹는지는 <b>확인되지 않았다.</b> 실계좌로 찍어 보고 0건이면 이 방식이 맞다.
     */
    private static final String NATION_US = "200";

    /**
     * 미국 거래국가에서 유효한 통화. {@link #NATION_US} 가 고정이므로 통화도 하나로 묶인다 —
     * 둘이 어긋나면 나무는 또 조용히 0건을 준다.
     */
    private static final String USD = "USD";

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

    /**
     * 캔들 봉투 — <b>{@code Output_0} 을 아예 선언하지 않는다.</b> NH 가 ⚠️ 를 단 자리라
     * 모양을 맞히는 대신 읽지 않기로 했다. 자세한 이유는 {@link NamuCandleEnvelope} 참고.
     */
    private static final ParameterizedTypeReference<
        NamuCandleEnvelope<NamuCandleDto.KrCandle>> KR_CANDLE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuCandleEnvelope<NamuCandleDto.GbCandle>> GB_CANDLE_TYPE =
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

    /**
     * 캔들 캐시. 시세 캐시와 사정이 다르다.
     *
     * <p><b>왜 필요한가</b> — 차트는 한 종목을 열 때만 부르니 시세보다 덜 위험해 보이지만,
     * 기간 탭(1주·1개월·3개월·1년)은 <b>전부 같은 일봉 첫 페이지</b>를 요청한다. 탭을 빠르게
     * 누르면 같은 요청이 연속으로 나가고, 여기에 화면의 실시간 폴링(분봉 15초·일봉 60초)이
     * 겹친다. 나무는 종목당 1콜이고 429 한도가 있다.
     *
     * <p><b>왜 상한을 두는가</b> — 시세 캐시는 키가 (사용자 × 종목)이라 저절로 유계지만,
     * 캔들 키에는 <b>커서가 들어간다.</b> 사용자가 과거로 팬할수록 키가 계속 생겨 그냥 두면
     * 무한히 는다. 넘치면 만료분을 먼저 걷고, 그래도 넘치면 통째로 비운다 —
     * 비워도 다음 요청이 상류에서 다시 받아 오므로 정확성에는 영향이 없다.
     */
    private final Map<String, CachedCandles> candleCache = new ConcurrentHashMap<>();

    private static final int CANDLE_CACHE_MAX_ENTRIES = 500;

    private record CachedCandles(CandlePage page, long expiresAtMillis) {
        boolean isFresh(long now) {
            return now < expiresAtMillis;
        }
    }

    /** 정렬·커서 계산에 쓰는 중간 표현. 문자열 timestamp 로 정렬하면 서머타임 경계에서 어긋난다. */
    private record TimedCandle(OffsetDateTime at, SecuritiesCandle candle) {
    }

    /**
     * 환율 캐시 — <b>사용자별</b>. 키는 {@code userRowId:통화}.
     *
     * <p><b>왜 사용자별인가</b> — 어느 경로가 이겼느냐가 사용자마다 다르고, 그 값의 뜻도 다르다.
     * 1순위(해외 잔고 {@code tdt_sby_bse_xcg_rt})는 <b>그 사용자 계좌의 외화 평가에 실제로
     * 적용된</b> 환율이라 계좌가 있는 사용자에게만 나오고, 2순위(해외 현재가
     * {@code currency_prc})는 계좌와 무관한 시장 기준환율이다. 둘은 소수점이 다를 수 있으므로
     * 한 통에 담으면 계좌가 있는 사용자에게 남의 시장환율이 나가 잔고 화면의 평가금액과
     * 어긋난다. 그래서 <b>최종 결과는 사용자별로만 캐시한다.</b>
     *
     * <p><b>왜 상한이 없나</b> — 캔들 캐시와 달리 키가 저절로 유계다. 통화는 USD 하나뿐이고
     * (미국 외는 상류에 나가기도 전에 접힌다) 나머지 축은 사용자라, 항목 수가 나무를 연동한
     * 사용자 수를 못 넘는다. 커서가 키에 들어가는 캔들 쪽만 상한이 필요하다.
     *
     * <p><b>실패도 캐시한다</b>(negative caching) — {@link CachedFx#rate()} 가 null 이면
     * "못 구했다" 를 기억하는 항목이다. 실패를 안 담으면 환율이 안 나오는 사용자가 매 요청마다
     * 3콜을 다시 내는데, 그게 정확히 429 를 부르는 모양이다.
     */
    private final Map<String, CachedFx> fxCache = new ConcurrentHashMap<>();

    /**
     * 환율 캐시 — <b>사용자 무관</b>. 키는 {@code 통화:폴백종목}.
     *
     * <p>2순위(해외 현재가)가 주는 {@code currency_prc} 는 계좌를 안 타는 <b>시장 시세</b>라
     * 누가 물어도 같은 값이다. 나무의 429 는 사용자가 아니라 <b>앱 단위</b> 한도를 말하므로
     * ({@code rsp_cd=IGW42902} "APP 호출 거래건수를 초과하였습니다", dev 실측 2026-08-28),
     * 여기서 사용자끼리 값을 나눠 쓰면 초과되는 그 한도가 직접 줄어든다.
     *
     * <p><b>성공한 값만 담는다.</b> 실패까지 나누면 한 사용자의 429·설정 오류가 다른 사용자의
     * 조회를 막는다 — 인증정보는 사용자별 키라 한도가 정말 앱 단위인지 확인되지 않았고,
     * 확인 전에는 남을 대신 벌주지 않는 쪽이 안전하다. 실패는 위 {@link #fxCache} 에만 남는다.
     */
    private final Map<String, CachedFx> fxQuoteCache = new ConcurrentHashMap<>();

    /** {@code rate} 가 null 일 수 있다 — "못 구했다" 는 사실도 캐시하기 때문이다. */
    private record CachedFx(BigDecimal rate, long expiresAtMillis) {
        boolean isFresh(long now) {
            return now < expiresAtMillis;
        }
    }

    /**
     * 환율 조회 한 경로의 결과. <b>"못 구했다" 와 "유량 제한에 걸렸다" 를 나눈다</b> —
     * 앞은 다음 경로로 넘어가라는 뜻이고, 뒤는 <b>넘어가지 말라</b>는 뜻이라 정반대다.
     * null 하나로는 그 차이를 실어 나를 수 없어 기록으로 만들었다.
     */
    private record FxLookup(BigDecimal rate, boolean rateLimited) {

        static final FxLookup MISSING = new FxLookup(null, false);
        static final FxLookup RATE_LIMITED = new FxLookup(null, true);

        static FxLookup of(BigDecimal rate) {
            return rate == null ? MISSING : new FxLookup(rate, false);
        }
    }

    @Override
    public PriceQuote getKrPrice(Long userRowId, String symbol, String marketCode) {
        NamuMarketDto.KrPrice p = namuApiClient.postObject(userRowId, KR_PRICE_PATH,
            Map.of("market_cd", krMarket(marketCode),
                   "iem_cd", symbol),
            KR_TYPE);
        return p == null ? null : quote(symbol, p.price(), KRW, p.previousClose(), p.change(), p.changeSign());
    }

    /** 미지정이면 KRX. 그 밖의 값은 나무 어휘가 아니면 거절한다 — 위 {@link #KR_MARKETS} 참고. */
    private String krMarket(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            return MARKET_KRX;
        }
        String normalized = marketCode.trim().toUpperCase();
        if (!KR_MARKETS.contains(normalized)) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_MARKET_UNSUPPORTED);
        }
        return normalized;
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

    // === 캔들(기간별시세) ===

    @Override
    public CandlePage getCandles(Long userRowId, CandleQuery query) {
        StockMaster stock = stockMasterResolver.resolve((StockMarket) null, query.symbol())
            .orElseThrow(() -> {
                log.warn("나무 캔들 - 마스터에 없는 종목: symbol={}", query.symbol());
                return new InvalidValueException(DeskErrorCode.SECURITIES_SYMBOL_INVALID);
            });

        String key = candleCacheKey(userRowId, stock, query);
        CachedCandles hit = candleCache.get(key);
        if (hit != null && hit.isFresh(System.currentTimeMillis())) {
            return hit.page();
        }

        CandlePage page = COUNTRY_KR.equals(stock.getCountryCode())
            ? krCandles(userRowId, stock, query)
            : gbCandles(userRowId, stock, query);
        cacheCandles(key, page);
        return page;
    }

    private CandlePage krCandles(Long userRowId, StockMaster stock, CandleQuery query) {
        boolean minute = query.interval() == CandleInterval.MINUTE_1;
        ZoneId zone = zoneOf(stock);

        Map<String, String> input = new LinkedHashMap<>();
        input.put("market_cd", MARKET_KRX);
        input.put("iem_cd", stock.getSymbol());
        input.put("gubun", minute ? KR_GUBUN_MINUTE : KR_GUBUN_DAY);
        input.put("edate", endDate(query.cursor(), zone));
        input.put("array_cnt", String.valueOf(query.size()));
        input.put("today_cls_code", TODAY_CLS_ALL);
        if (minute) {
            // 국내는 "분/초/틱시 입력" 이라 일봉에는 넣지 않는다.
            input.put("xtick", KR_XTICK_1MIN);
        }

        List<NamuCandleDto.KrCandle> rows = namuApiClient
            .exchange(userRowId, KR_PERIOD_PATH, input, KR_CANDLE_TYPE)
            .items();

        List<TimedCandle> bars = rows.stream()
            .map(r -> timed(r.date(), r.time(), r.open(), r.high(), r.low(), r.close(), r.volume(),
                zone, KRW, stock.getSymbol()))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(TimedCandle::at))
            .toList();
        return page(bars, rows.size(), query);
    }

    private CandlePage gbCandles(Long userRowId, StockMaster stock, CandleQuery query) {
        boolean minute = query.interval() == CandleInterval.MINUTE_1;
        ZoneId zone = zoneOf(stock);

        // 해외는 8개가 전부 필수다 — 하나만 빠져도 나무가 거절한다.
        Map<String, String> input = new LinkedHashMap<>();
        input.put("iem_cd", stock.getSymbol());
        input.put("end_dt", endDate(query.cursor(), zone));
        input.put("count", String.valueOf(query.size()));
        input.put("maxavg", GB_MAXAVG);
        input.put("gubun", minute ? GB_GUBUN_MINUTE : GB_GUBUN_DAY);
        input.put("xtick", GB_XTICK_1UNIT);
        input.put("today_cls", TODAY_CLS_ALL);
        input.put("market_cls", GB_MARKET_REGULAR);

        List<NamuCandleDto.GbCandle> rows = namuApiClient
            .exchange(userRowId, GB_PERIOD_PATH, input, GB_CANDLE_TYPE)
            .items();

        String currency = stock.getCurrency() == null || stock.getCurrency().isBlank()
            ? USD : stock.getCurrency();
        List<TimedCandle> bars = rows.stream()
            .map(r -> timed(r.date(), r.time(), r.open(), r.high(), r.low(), r.close(), r.volume(),
                zone, currency, stock.getSymbol()))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(TimedCandle::at))
            .toList();
        return page(bars, rows.size(), query);
    }

    /**
     * 다음 페이지 커서를 정한다.
     *
     * <p><b>나무엔 불투명 커서가 없다.</b> 페이지를 거슬러 올라가는 수단은 종료일
     * ({@code edate}/{@code end_dt})뿐이라, 커서로 <b>이번 페이지에서 가장 오래된 봉의
     * 하루 전</b>을 돌려준다. 다음 요청이 그 날짜까지 다시 읽어 내려간다.
     *
     * <p><b>분봉은 커서를 주지 않는다(null).</b> 종료일이 날짜 단위라 하루 안에서 더 과거로
     * 갈 방법이 없다 — 같은 날짜를 다시 주면 방금 받은 봉이 그대로 오고, 하루를 빼면 그날
     * 오전이 통째로 날아간다. 어느 쪽도 "이어서 읽기" 가 아니다. 화면의 {@code 1D} 탭은
     * 첫 페이지(200봉)로 채우도록 되어 있어 실제로 부족하지 않다.
     *
     * <p>요청한 수보다 적게 왔으면 상장 이전까지 다 읽은 것으로 보고 끝낸다.
     */
    private static CandlePage page(List<TimedCandle> bars, int rawRowCount, CandleQuery query) {
        List<SecuritiesCandle> candles = bars.stream().map(TimedCandle::candle).toList();
        if (query.interval() == CandleInterval.MINUTE_1 || bars.isEmpty() || rawRowCount < query.size()) {
            return new CandlePage(candles, null);
        }
        LocalDate oldest = bars.get(0).at().toLocalDate();
        return new CandlePage(candles, YMD.format(oldest.minusDays(1)));
    }

    /**
     * 조회 종료일. 첫 페이지면 거래소 현지 오늘.
     *
     * <p><b>알아볼 수 없는 커서는 첫 페이지로 되돌린다.</b> 우리가 만드는 커서는
     * {@code YYYYMMDD} 지만, 사용자가 차트를 열어 둔 채 기본 증권사를 바꾸면 화면이 들고 있던
     * <b>토스 커서</b>(불투명 문자열)가 여기로 넘어온다. 400 으로 거절하면 그때까지 잘 보던
     * 차트가 에러로 바뀐다 — 첫 페이지를 다시 주면 화면은 이미 가진 봉과 겹치는 것을 보고
     * 스스로 로딩을 멈춘다.
     */
    private static String endDate(String cursor, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (cursor == null || cursor.isBlank()) {
            return YMD.format(today);
        }
        try {
            return YMD.format(LocalDate.parse(cursor.trim(), YMD));
        } catch (DateTimeException e) {
            log.debug("나무 캔들 - 나무 커서가 아니다. 첫 페이지로 되돌린다: cursor={}", cursor);
            return YMD.format(today);
        }
    }

    /**
     * 봉 한 줄을 공통 모양으로 옮긴다. 날짜나 종가가 없으면 <b>그 봉만</b> 버린다 —
     * 한 줄 때문에 차트 전체를 접지 않는다.
     *
     * <p>시가·고가·저가가 비면 종가로 채운다. 그대로 두면 화면이 {@code NaN} 을 받아
     * 캔들 시리즈가 통째로 그려지지 않는다(값 하나가 아니라 차트가 사라진다).
     */
    private static TimedCandle timed(String rawDate, String rawTime, String open, String high,
                                     String low, String close, String volume,
                                     ZoneId zone, String currency, String symbol) {
        LocalDate date = candleDate(rawDate);
        String closeText = text(close, null);
        if (date == null || closeText == null) {
            log.debug("나무 캔들 봉 버림 - symbol={}, date={}, close={}", symbol, rawDate, close);
            return null;
        }
        OffsetDateTime at = date.atTime(candleTime(rawTime)).atZone(zone).toOffsetDateTime();
        return new TimedCandle(at, new SecuritiesCandle(
            CANDLE_TIMESTAMP.format(at),
            text(open, closeText), text(high, closeText), text(low, closeText), closeText,
            text(volume, "0"), currency));
    }

    private static String text(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    private static LocalDate candleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YMD);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * {@code HHmmss}. <b>일봉은 시각이 비어 온다</b> — 그때는 자정으로 둔다(날짜 라벨만 맞으면 된다).
     * 자릿수가 모자란 값({@code HHmm})도 0 으로 채워 받는다.
     */
    private static LocalTime candleTime(String raw) {
        String digits = raw == null ? "" : raw.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return LocalTime.MIDNIGHT;
        }
        String padded = (digits + "000000").substring(0, 6);
        try {
            return LocalTime.of(Integer.parseInt(padded.substring(0, 2)),
                Integer.parseInt(padded.substring(2, 4)),
                Integer.parseInt(padded.substring(4, 6)));
        } catch (DateTimeException | NumberFormatException e) {
            return LocalTime.MIDNIGHT;
        }
    }

    private static ZoneId zoneOf(StockMaster stock) {
        return MARKET_ZONES.getOrDefault(stock.getCountryCode(), DEFAULT_ZONE);
    }

    private static String candleCacheKey(Long userRowId, StockMaster stock, CandleQuery query) {
        return userRowId + ":" + stock.getMarketCode().name() + ":" + stock.getSymbol()
            + ":" + query.interval().getCode() + ":" + query.size()
            + ":" + (query.cursor() == null ? "" : query.cursor());
    }

    private void cacheCandles(String key, CandlePage page) {
        long ttlMillis = namuProperties.getCandleCacheTtlSeconds() * 1000L;
        if (ttlMillis <= 0) {
            return;
        }
        if (candleCache.size() >= CANDLE_CACHE_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            candleCache.entrySet().removeIf(e -> !e.getValue().isFresh(now));
            if (candleCache.size() >= CANDLE_CACHE_MAX_ENTRIES) {
                log.warn("나무 캔들 캐시 상한 초과 - 비운다: {}건", candleCache.size());
                candleCache.clear();
            }
        }
        candleCache.put(key, new CachedCandles(page, System.currentTimeMillis() + ttlMillis));
    }

    // === 계좌·잔고 ===

    /**
     * 계좌 목록. <b>거르지 않는다</b> — 사용자가 자기 계좌를 다 보는 건 맞다. 대신 현재 환경에서
     * 쓸 수 있는지를 {@code usable} 로 표시해 화면이 고를 수 있게 한다. 거르는 건 잔고 조회에
     * 쓸 계좌를 <b>자동으로</b> 고를 때다({@link #resolveAccountNo}).
     */
    @Override
    public List<NamuAccountDto.Account> getAccounts(Long userRowId) {
        // 입력 파라미터가 없는 조회지만 봉투는 그대로 지킨다 — 서버가 Input_0 을 요구한다.
        // 계좌목록은 우리가 쓰는 것 중 유일하게 Output_0 이 배열이다.
        NamuEnvironment env = namuProperties.getEnvironment();
        return namuApiClient.postList(userRowId, ACCOUNT_PATH, Map.of(), ACCOUNT_TYPE).stream()
            .map(a -> a.withUsable(env.accepts(a.accountType())))
            .toList();
    }

    @Override
    public NamuAccountDto.Holdings getHoldings(Long userRowId, String accountNo, String currency) {
        String account = resolveAccountNo(userRowId, accountNo);
        if (account == null) {
            return NamuAccountDto.Holdings.empty("", currency);
        }
        return KRW.equalsIgnoreCase(currency)
            ? krHoldings(userRowId, account)
            : gbHoldings(userRowId, account, currency);
    }

    /**
     * 원화 환산 환율. <b>두 경로를 순서대로 시도한다.</b>
     *
     * <p><b>USD 만 구할 수 있다</b> — 나무 해외 연동이 미국 고정이라({@link #NATION_US})
     * 다른 통화는 물어볼 곳이 없다. 미지원 통화는 null 로 접는다.
     *
     * <h2>폴백 순서와 근거</h2>
     * <ol>
     *   <li><b>해외 잔고</b>({@code tdt_sby_bse_xcg_rt}) — <b>그 계좌의 외화 평가에 실제로
     *       적용된 환율</b>이다. 같은 값을 써야 잔고 화면의 평가금액과 자산 평가가 어긋나지
     *       않으므로, 얻을 수 있으면 이쪽이 이긴다. 다만 <b>계좌 + USD 보유 종목이 둘 다</b>
     *       있어야 나온다(환율이 계좌 요약이 아니라 종목 행에 실려 오기 때문).</li>
     *   <li><b>해외 현재가</b>({@code currency_prc}) — 요청에 {@code iem_cd} 하나만 필요해
     *       <b>계좌도 보유도 없이</b> 얻는다. 대신 계좌가 아니라 <b>시세 기준</b> 환율이라
     *       1번과 소수점이 다를 수 있다. 그래서 폴백이다.</li>
     * </ol>
     *
     * <p><b>예전 주석이 "해외 잔고가 문서화된 유일한 경로" 라고 단정했는데 사실이 아니다.</b>
     * 그 말을 믿고 폴백을 안 두는 바람에 <b>해외 계좌가 없는 사용자는 환율을 영영 못 구했고</b>
     * 화면이 외화 평가를 통째로 접었다. 나무 공식 스펙상 {@code /gbstock/quote/v1/current} 의
     * {@code Output_0} 에 {@code currency_prc} 가 있고, 그 요청은 종목코드만 받는다.
     *
     * <p>세 번째 후보로 {@code /gbstock/inquiry/v1/margin}(해외증거금 통화별조회)의
     * {@code sby_bse_xcg_rt} 도 있다 — <b>통화별 배열</b>이라 USD 외 통화까지 준다. 지금은 안
     * 쓴다: 계좌를 요구하는데 그 조건은 2번이 이미 계좌 없이 덮으므로 커버리지가 늘지 않는다.
     * 나중에 <b>USD 외 통화를 지원하게 되면</b> 그때 여기 끼울 자리다.
     *
     * <p><b>여기서는 던지지 않는다</b> — 잔고 화면은 미지원 통화를 400 으로 거절하지만
     * ({@link #requireUsCompatible}), 이 경로는 자산 평가가 부르는 자리라 예외가 나가면
     * 환율 하나 때문에 평가 전체가 멈춘다.
     *
     * <h2>캐시와 429</h2>
     * <b>한 번 물으면 최대 3콜이 100ms 안에 몰려 나간다</b> — 계좌목록 + 해외잔고
     * (+ 폴백 시 해외현재가). 캐시가 없던 동안 그게 화면이 부를 때마다 그대로 나갔고,
     * 나무는 그중 뒤 두 개를 <b>429 로 거절했다</b>({@code IGW42902} "APP 호출 거래건수를
     * 초과하였습니다", dev 실측 2026-08-28 · 두 경로가 30~40ms 간격으로 짝지어 8건).
     * 그래서 둘을 넣는다.
     *
     * <ol>
     *   <li><b>결과를 캐시한다</b> — 성공은 {@code app.namu.fx-cache-ttl-seconds}(기본 10분),
     *       실패도 {@code app.namu.fx-failure-cache-ttl-seconds}(기본 1분) 동안 기억한다.
     *       기준환율은 하루짜리 고정값이라 시세(20초)보다 훨씬 길게 잡아도 안전하다.</li>
     *   <li><b>429 면 폴백을 타지 않는다</b> — 429 는 "지금 너무 많이 부르고 있다" 는 신호라
     *       곧바로 2순위를 치면 <b>같은 초에 429 를 한 번 더</b> 맞고 상류 부담만 키운다.
     *       {@code app.namu.fx-rate-limit-backoff-seconds}(기본 5분) 쉬었다 다음 기회에
     *       다시 시도한다.</li>
     * </ol>
     *
     * @see <a href="https://www.nhplug.com/llms-full.txt">NH PLUG OpenAPI 전체 스펙</a>
     */
    @Override
    public BigDecimal getFxRate(Long userRowId, String currency) {
        String want = currency == null || currency.isBlank() ? USD : currency.trim().toUpperCase();
        if (!USD.equals(want)) {
            log.debug("나무 환율 조회 불가 - 미국(USD)만 지원한다: 요청={} (userRowId={})", want, userRowId);
            return null;
        }

        String key = userRowId + ":" + want;
        CachedFx hit = fxCache.get(key);
        if (hit != null && hit.isFresh(System.currentTimeMillis())) {
            return hit.rate();
        }

        FxLookup fromBalance = fxRateFromBalance(userRowId, want);
        if (fromBalance.rate() != null) {
            return cacheFxRate(key, fromBalance.rate(), namuProperties.getFxCacheTtlSeconds());
        }
        if (fromBalance.rateLimited()) {
            // 여기서 폴백을 타면 같은 429 를 한 번 더 맞는다. 원인이 "호출이 많다" 인데
            // 호출을 더 내는 셈이라, 유일하게 맞는 행동은 지금 안 부르는 것이다.
            log.warn("나무 환율 - 유량 제한(429). 시세 폴백을 건너뛰고 {}초 쉰다 (userRowId={})",
                namuProperties.getFxRateLimitBackoffSeconds(), userRowId);
            return cacheFxRate(key, null, namuProperties.getFxRateLimitBackoffSeconds());
        }

        FxLookup fromQuote = fxRateFromQuote(userRowId, want);
        if (fromQuote.rate() != null) {
            return cacheFxRate(key, fromQuote.rate(), namuProperties.getFxCacheTtlSeconds());
        }
        return cacheFxRate(key, null, fromQuote.rateLimited()
            ? namuProperties.getFxRateLimitBackoffSeconds()
            : namuProperties.getFxFailureCacheTtlSeconds());
    }

    /**
     * 환율 결과를 사용자별 캐시에 담고 그대로 돌려준다. {@code rate} 가 null 이어도 담는다 —
     * 못 구했다는 사실을 안 담으면 실패한 사용자가 매 요청마다 상류를 다시 친다.
     *
     * @param ttlSeconds 0 이하면 담지 않는다(캐시를 끈 것)
     */
    private BigDecimal cacheFxRate(String key, BigDecimal rate, int ttlSeconds) {
        if (ttlSeconds > 0) {
            fxCache.put(key, new CachedFx(rate, System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return rate;
    }

    /**
     * 1순위 — 해외 잔고의 당일매매기준환율. 계좌와 {@code want} 보유 종목이 둘 다 있어야 나온다.
     *
     * <p>환율은 <b>종목별 행(Output_1)</b>에 실려 온다 — 계좌 요약이 아니다. 종목마다 통화가
     * 달라 계좌 단위로 환율 하나를 들 수 없는 구조라서다.
     *
     * @return 값 · 또는 {@link FxLookup#MISSING}(2순위로 넘어가라) ·
     *         또는 {@link FxLookup#RATE_LIMITED}(넘어가지 마라)
     */
    private FxLookup fxRateFromBalance(Long userRowId, String want) {
        try {
            // 계좌 해석도 try 안이다 — 환경에 맞는 계좌가 없으면 예외가 나는데, 그게 자산 평가
            // 전체를 무너뜨리면 안 된다. 잔고 화면에서는 그대로 던져 원인을 보여주고,
            // 여기서는 폴백으로 넘어간다(부분합으로 금액을 왜곡하지 않는 기존 규칙).
            String account = resolveAccountNo(userRowId, null);
            if (account == null) {
                log.debug("나무 환율 - 계좌가 없어 잔고 경로를 건너뛴다 (userRowId={})", userRowId);
                return FxLookup.MISSING;
            }
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
                log.debug("나무 환율 - 잔고에 {} 보유 종목이 없어 시세 경로로 넘어간다 (userRowId={}, 종목={}건)",
                    want, userRowId, items.size());
            }
            return FxLookup.of(rate);
        } catch (NamuRateLimitException e) {
            // 이 catch 가 RuntimeException 보다 먼저 와야 한다. 아래로 흘리면 429 가
            // 일반 실패로 뭉개져 곧바로 폴백이 나가고, 그게 지금 고치는 회귀 그 자체다.
            log.debug("나무 환율 - 잔고 경로가 유량 제한에 걸렸다 (userRowId={})", userRowId);
            return FxLookup.RATE_LIMITED;
        } catch (RuntimeException e) {
            log.debug("나무 환율 - 잔고 경로 실패, 시세 경로로 넘어간다 (userRowId={}): {}", userRowId, e.getMessage());
            return FxLookup.MISSING;
        }
    }

    /**
     * 2순위 — 해외 현재가의 {@code currency_prc}. <b>계좌도 보유 종목도 필요 없다.</b>
     *
     * <p>물어볼 종목은 {@code app.namu.fx-probe-symbol} 이 정한다. 비어 있으면 폴백을 끈다.
     *
     * <p><b>응답 통화를 반드시 확인한다</b> — {@code currency_prc} 는 그 종목의
     * {@code currency_unit} 1단위 원화 가격이다. 설정된 종목이 미국 상장이 아니게 되면
     * (티커 재사용·거래소 이전) 엉뚱한 통화의 환율을 USD 환율로 쓰게 되는데, 그건 화면에
     * 그럴듯한 숫자로 나가 아무도 못 알아챈다. 그래서 다르면 쓰지 않고 접는다.
     *
     * <p>얻은 값은 계좌를 안 타는 시장 기준환율이라 <b>사용자끼리 나눠 쓴다</b>
     * ({@link #fxQuoteCache}). 실패는 안 나눈다 — 이유는 그 필드 주석 참고.
     *
     * @return 값 · 또는 {@link FxLookup#MISSING} · 또는 {@link FxLookup#RATE_LIMITED}
     */
    private FxLookup fxRateFromQuote(Long userRowId, String want) {
        String probe = namuProperties.getFxProbeSymbol();
        if (probe == null || probe.isBlank()) {
            log.debug("나무 환율 - 시세 폴백이 꺼져 있다(app.namu.fx-probe-symbol 비어 있음) (userRowId={})", userRowId);
            return FxLookup.MISSING;
        }
        String symbol = probe.trim();

        // 폴백 종목을 키에 넣는다 — 설정이 바뀌면 옛 종목으로 받아 둔 값이 살아 있으면 안 된다.
        String key = want + ":" + symbol;
        CachedFx hit = fxQuoteCache.get(key);
        if (hit != null && hit.isFresh(System.currentTimeMillis())) {
            return FxLookup.of(hit.rate());
        }

        try {
            NamuMarketDto.GbPrice p = namuApiClient.postObject(userRowId, GB_PRICE_PATH,
                Map.of("iem_cd", symbol), GB_TYPE);
            if (p == null) {
                log.warn("나무 환율 미확보 - 시세 응답이 비었다 (userRowId={}, 종목={})", userRowId, symbol);
                return FxLookup.MISSING;
            }
            if (!want.equalsIgnoreCase(p.currency())) {
                log.warn("나무 환율 미확보 - 폴백 종목의 통화가 다르다: 기대={} 실제={} (userRowId={}, 종목={}). "
                        + "app.namu.fx-probe-symbol 을 미국 상장 종목으로 바꿔라",
                    want, p.currency(), userRowId, symbol);
                return FxLookup.MISSING;
            }
            BigDecimal rate = decimal(p.exchangeRate());
            if (rate == null || rate.signum() <= 0) {
                log.warn("나무 환율 미확보 - 시세의 환율 필드가 비었다 (userRowId={}, 종목={}, currency_prc={})",
                    userRowId, symbol, p.exchangeRate());
                return FxLookup.MISSING;
            }
            log.debug("나무 환율 - 시세 폴백으로 확보 (userRowId={}, 종목={}, 환율={})", userRowId, symbol, rate);
            long ttlMillis = namuProperties.getFxCacheTtlSeconds() * 1000L;
            if (ttlMillis > 0) {
                fxQuoteCache.put(key, new CachedFx(rate, System.currentTimeMillis() + ttlMillis));
            }
            return FxLookup.of(rate);
        } catch (NamuRateLimitException e) {
            log.warn("나무 환율 조회 실패 - 시세 폴백이 유량 제한에 걸렸다 (userRowId={}, 종목={})", userRowId, symbol);
            return FxLookup.RATE_LIMITED;
        } catch (RuntimeException e) {
            log.warn("나무 환율 조회 실패 - 시세 폴백 (userRowId={}, 종목={}): {}", userRowId, symbol, e.getMessage());
            return FxLookup.MISSING;
        }
    }

    /**
     * 잔고 조회에 쓸 계좌를 정한다.
     *
     * <p><b>계좌구분을 반드시 본다.</b> 나무는 {@code acct_type} 이 그 계좌를 쓸 수 있는 도메인을
     * 정한다(운영 01·02 / 모의투자 03). 예전에는 {@code accounts.get(0)} 으로 첫 계좌를 집었는데,
     * 실제 사용자 목록이 {@code 03·03·01·03} 순으로 와서 운영 도메인에 모의투자 계좌가 나갔다 —
     * {@code rsp_cd=11165} "계좌번호를 잘못 입력하셨습니다". 목록 순서는 우리가 정하는 게 아니다.
     *
     * <p>넘겨받은 계좌도 그냥 믿지 않는다. 남의 환경 계좌를 그대로 태우면 같은 자리에서 다시
     * 업스트림 오류로 터지고, 502 + "API 호출에 실패했습니다" 만 남아 원인을 알 수 없다.
     *
     * @return 쓸 계좌번호. 계좌가 <b>한 건도 없으면</b> null — 호출부가 빈 잔고로 본다
     *         (연동은 됐는데 계좌가 없는 상태는 오류가 아니다)
     * @throws InvalidValueException 넘겨받은 계좌가 목록에 없거나 다른 환경 계좌일 때,
     *         또는 계좌는 있는데 현재 환경에 맞는 게 하나도 없을 때
     */
    private String resolveAccountNo(Long userRowId, String requested) {
        NamuEnvironment env = namuProperties.getEnvironment();
        List<NamuAccountDto.Account> accounts = getAccounts(userRowId);

        if (requested != null && !requested.isBlank()) {
            String want = requested.trim();
            NamuAccountDto.Account matched = accounts.stream()
                .filter(a -> want.equals(a.accountNo() == null ? null : a.accountNo().trim()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("나무 잔고 - 목록에 없는 계좌 지정: userRowId={}, account={}",
                        userRowId, maskAccountNo(want));
                    return new InvalidValueException(DeskErrorCode.SECURITIES_ACCOUNT_NOT_FOUND);
                });
            if (!env.accepts(matched.accountType())) {
                log.warn("나무 잔고 - 환경과 다른 구분의 계좌 지정: env={}, acctType={}, account={}",
                    env, matched.accountType(), maskAccountNo(want));
                throw new InvalidValueException(DeskErrorCode.SECURITIES_ACCOUNT_ENVIRONMENT_MISMATCH);
            }
            return want;
        }

        if (accounts.isEmpty()) {
            return null;
        }

        NamuAccountDto.Account picked = accounts.stream()
            .filter(a -> env.accepts(a.accountType()))
            .findFirst()
            .orElseThrow(() -> {
                log.warn("나무 잔고 - {} 환경에 맞는 계좌가 없다: userRowId={}, 보유 구분={}, 유효 구분={}",
                    env, userRowId, accounts.stream().map(NamuAccountDto.Account::accountType).toList(),
                    env.getAccountTypes());
                return new InvalidValueException(DeskErrorCode.SECURITIES_ACCOUNT_ENVIRONMENT_UNAVAILABLE);
            });

        log.debug("나무 잔고 계좌 선택 - env={}, acctType={}, account={} ({}건 중)",
            env, picked.accountType(), maskAccountNo(picked.accountNo()), accounts.size());
        return picked.accountNo();
    }

    /**
     * 로그용 계좌번호 마스킹 — <b>뒤 4자리만</b> 남긴다.
     *
     * <p>이 레포는 평문 시크릿이 로그로 샌 적이 있다(커밋 {@code 516de21}, 재발 {@code #253}).
     * 계좌번호도 같은 부류다. {@code RequestResponseLoggingFilter} 가 HTTP 본문·쿼리를 막지만
     * 서비스가 직접 찍는 로그는 거기 안 걸리므로 여기서 막는다.
     */
    static String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "(없음)";
        }
        String trimmed = accountNo.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
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
     * 해외 잔고 요청. 거래국가는 {@link #NATION_US} 로 고정이다.
     *
     * <p>{@code cur_cd} 는 문서상 {@code KRW}=전체(원화 환산) · {@code USD}/{@code CNY}/
     * {@code HKD}/{@code JPY}=해당 통화다. <b>미지정이면 USD</b> — 국가가 미국으로 고정이라
     * 이 경로에서 뜻이 통하는 통화가 그것뿐이다.
     *
     * @throws InvalidValueException 미국에서 거래되지 않는 통화를 물었을 때
     *         — {@link #requireUsCompatible} 참고
     */
    private static Map<String, String> gbBalanceInput(String account, String currency) {
        String cur = currency == null || currency.isBlank() ? USD : currency.trim().toUpperCase();
        requireUsCompatible(cur);
        return Map.of(
            "act_no", account,
            "qut_iqr_dit_cd", "1",          // 정규장
            "fc_sec_trd_nat_cd", NATION_US, // 미국 — "" 는 0건을 부른다. 위 상수 주석 참고
            "cur_cd", cur,
            "xns_dit_cd", "1"               // 비용 포함 — 실제 손익에 가깝다
        );
    }

    /**
     * 통화가 미국 거래국가와 맞는지 본다. <b>안 맞으면 400 으로 거절한다.</b>
     *
     * <p>거래국가를 {@code 200}(미국)으로 박은 순간 {@code cur_cd} 는 자유 파라미터가 아니게
     * 됐다. {@code JPY} 를 물으면 "미국 계좌에서 엔화 종목을 찾아 달라" 는 모순된 요청이고,
     * 나무는 그걸 <b>에러 없이 0건</b>으로 답한다 — 이 클래스가 방금 고친 버그와 정확히 같은
     * 모양이다.
     *
     * <p><b>왜 통화를 USD 로 슬쩍 바꾸지 않는가.</b> 그러면 JPY 를 물은 화면에 달러 금액이
     * {@code JPY} 라벨을 달고 나간다. 빈 화면보다 나쁘다 — 틀린 걸 눈치챌 방법이 없다.
     * <b>왜 빈 결과 + warn 이 아닌가.</b> warn 은 로그에만 남고 화면에는 "보유 중인 종목이
     * 없어요" 가 뜬다. 화면이 정상으로 보이니 아무도 신고하지 않는다 — 이번 버그가
     * 실측하러 가기 전까지 안 잡힌 이유가 그것이다.
     *
     * <p>같은 판단을 {@link #KR_MARKETS} 에서 이미 했다 — 조용히 틀리느니 400 으로 시끄럽게
     * 틀리는 게 낫다. 지금 화면이 보내는 값은 {@code KRW}(국내 탭)와 {@code USD}(해외 탭)
     * 둘뿐이라 실제로 막히는 요청도 없다.
     *
     * <p>{@code KRW} 도 여기서는 거절이다. 원화 조회는 국내 엔드포인트로 가야 하고
     * ({@link #getHoldings} 가 갈라 준다), 이 경로의 {@code KRW}=전체는 우리가 지원하지 않는
     * 국가까지 포함하는 뜻이라 반쪽짜리 답이 된다.
     */
    private static void requireUsCompatible(String currency) {
        if (!USD.equals(currency)) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_CURRENCY_UNSUPPORTED);
        }
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
