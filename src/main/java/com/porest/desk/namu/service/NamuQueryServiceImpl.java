package com.porest.desk.namu.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.type.NamuEnvironment;
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
     * 환율은 <b>종목별 행(Output_1)</b>에 실려 온다 — 계좌 요약이 아니다. 종목마다 통화가
     * 달라 계좌 단위로 환율 하나를 들 수 없는 구조라서다.
     *
     * <p>그래서 제약이 둘 붙는다. 하나, <b>USD 만 구할 수 있다</b> — 잔고 조회의 거래국가가
     * 미국 고정이라({@link #NATION_US}) 다른 통화는 물어볼 곳이 없다. 둘, <b>USD 보유 종목이
     * 하나도 없으면 못 구한다.</b> 어느 쪽이든 null 로 접어 외화 평가만 건너뛴다
     * (부분합으로 금액을 왜곡하지 않는 기존 규칙).
     *
     * <p><b>여기서는 던지지 않는다</b> — 잔고 화면은 미지원 통화를 400 으로 거절하지만
     * ({@link #requireUsCompatible}), 이 경로는 자산 평가가 부르는 자리라 예외가 나가면
     * 환율 하나 때문에 평가 전체가 멈춘다.
     */
    @Override
    public BigDecimal getFxRate(Long userRowId, String currency) {
        String want = currency == null || currency.isBlank() ? USD : currency.trim().toUpperCase();
        if (!USD.equals(want)) {
            log.debug("나무 환율 조회 불가 - 미국(USD)만 지원한다: 요청={} (userRowId={})", want, userRowId);
            return null;
        }
        try {
            // 계좌 해석도 try 안이다 — 환경에 맞는 계좌가 없으면 예외가 나는데, 그게 자산 평가
            // 전체를 무너뜨리면 안 된다. 잔고 화면에서는 그대로 던져 원인을 보여주고,
            // 여기서는 외화 평가만 접는다(부분합으로 금액을 왜곡하지 않는 기존 규칙).
            String account = resolveAccountNo(userRowId, null);
            if (account == null) {
                log.debug("나무 환율 조회 불가 - 계좌 없음 (userRowId={})", userRowId);
                return null;
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
                log.warn("나무 환율 미확보 - {} 보유 종목이 없거나 환율 필드가 비었다 (userRowId={}, 종목={}건)",
                    want, userRowId, items.size());
            }
            return rate;
        } catch (RuntimeException e) {
            log.warn("나무 환율 조회 실패 (userRowId={}): {}", userRowId, e.getMessage());
            return null;
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
