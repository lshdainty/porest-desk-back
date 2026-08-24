package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final StockMasterRepository stockMasterRepository;

    @Override
    public PriceQuote getKrPrice(Long userRowId, String symbol, String marketCode) {
        NamuMarketDto.KrPrice p = namuApiClient.postObject(userRowId, KR_PRICE_PATH,
            Map.of("market_cd", marketCode == null || marketCode.isBlank() ? MARKET_KRX : marketCode,
                   "iem_cd", symbol),
            KR_TYPE);
        return p == null ? null : quote(symbol, p.price(), KRW);
    }

    @Override
    public PriceQuote getGbPrice(Long userRowId, String symbol) {
        NamuMarketDto.GbPrice p = namuApiClient.postObject(userRowId, GB_PRICE_PATH,
            Map.of("iem_cd", symbol), GB_TYPE);
        return p == null ? null : quote(symbol, p.price(), p.currency());
    }

    /**
     * 국내·해외를 섞어 조회한다.
     *
     * <p>나무는 <b>종목 다건 한 번에</b> 주는 시세 API 가 없어 종목마다 한 콜씩 나간다
     * (토스는 콤마 구분 다건이다). 보유 종목 수만큼 호출이 나가므로 자산 평가 경로에서만 쓰고,
     * 한 종목이 실패해도 나머지는 살린다 — 전체를 접으면 평가가 통째로 멈춘다.
     *
     * <p>국내인지 해외인지는 {@code stock_master} 가 정한다. 심볼이 여러 시장에 걸치면
     * (해외 중복 티커) 어느 쪽인지 확정할 수 없어 건너뛴다 — 엉뚱한 시장 시세를 자산에
     * 반영하느니 그 종목만 빼는 게 낫다.
     */
    @Override
    public List<PriceQuote> getPrices(Long userRowId, List<String> symbols) {
        List<PriceQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            List<StockMaster> matches = stockMasterRepository.findAllActiveBySymbol(symbol);
            if (matches.size() != 1) {
                log.debug("나무 시세 건너뜀 - 종목 특정 불가: symbol={}, 후보={}건", symbol, matches.size());
                continue;
            }
            StockMaster stock = matches.get(0);
            try {
                PriceQuote quote = "KR".equals(stock.getCountryCode())
                    ? getKrPrice(userRowId, symbol, MARKET_KRX)
                    : getGbPrice(userRowId, symbol);
                if (quote != null) {
                    quotes.add(quote);
                }
            } catch (RuntimeException e) {
                log.warn("나무 시세 조회 실패 - symbol={}: {}", symbol, e.getMessage());
            }
        }
        return quotes;
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

    private static PriceQuote quote(String symbol, String rawPrice, String currency) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        try {
            return new PriceQuote(symbol, new BigDecimal(rawPrice.trim()),
                currency == null || currency.isBlank() ? KRW : currency);
        } catch (NumberFormatException e) {
            log.warn("나무 시세 파싱 실패: symbol={}, price={}", symbol, rawPrice);
            return null;
        }
    }
}
