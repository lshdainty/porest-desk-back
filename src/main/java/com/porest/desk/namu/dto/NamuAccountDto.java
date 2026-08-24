package com.porest.desk.namu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 나무증권 계좌·잔고 응답 DTO.
 *
 * <p>금액은 <b>문자열 그대로</b> 둔다. 나무가 문자열로 주는 이유가 정밀도 보존이고,
 * double 로 받으면 그 의도가 무너진다. 숫자로 써야 하는 곳에서만 {@code BigDecimal} 로 바꾼다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 를 붙인 이유 — 잔고 응답 필드가 30개가
 * 넘는데 우리가 쓰는 건 몇 개뿐이다. 나무가 필드를 늘릴 때마다 역직렬화가 깨지면 안 된다.
 */
public final class NamuAccountDto {

    private NamuAccountDto() {
    }

    /** 계좌 1건 ({@code POST /n2/acctinfo} 의 Output_0). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
        @JsonProperty("acct_no") String accountNo,
        @JsonProperty("acct_type") String accountType
    ) {
    }

    /**
     * 국내 잔고 요약 (Output_0). 계좌 단위 집계다 — 종목별은 {@link KrHolding}(Output_1).
     *
     * @param totalAssetAmount 총자산 {@code tot_aet_amt}
     * @param totalBuyAmount   총매입 {@code tot_byn_amt}
     * @param totalEvalAmount  총평가 {@code tot_eal_amt}
     * @param totalProfitLoss  총평가손익 {@code tot_eal_pls}
     * @param profitRate       수익률 {@code pft_rt}
     * @param deposit          예수금 {@code dca}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrBalanceSummary(
        @JsonProperty("tot_aet_amt") String totalAssetAmount,
        @JsonProperty("tot_byn_amt") String totalBuyAmount,
        @JsonProperty("tot_eal_amt") String totalEvalAmount,
        @JsonProperty("tot_eal_pls") String totalProfitLoss,
        @JsonProperty("pft_rt") String profitRate,
        @JsonProperty("dca") String deposit
    ) {
    }

    /** 국내 종목별 보유 (Output_1). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrHolding(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("itg_bnc_qty") String quantity,
        @JsonProperty("phs_pr") String avgPrice,
        @JsonProperty("now_pr") String currentPrice,
        @JsonProperty("eal_amt") String evalAmount,
        @JsonProperty("eal_pls_amt") String profitLoss
    ) {
    }

    /**
     * 해외 잔고 요약 (Output_0).
     *
     * <p>{@code tdt_sby_bse_xcg_rt}(당일매매기준환율)가 여기 있다 — 나무는 환율 전용 조회가
     * 없고 지수·환율 통합 API 의 코드값이 문서에 없어서, <b>환율을 얻을 수 있는 유일한
     * 문서화된 경로</b>다.
     *
     * @param totalAssetAmount 총자산(원화) {@code tot_aet_amt}
     * @param evalAmountSum    평가금액 합 {@code eal_amt_sum}
     * @param profitLossSum    평가손익 합 {@code eal_pls_sum_amt}
     * @param krwProfitRate    원화 수익률 {@code krw_pft_rt}
     * @param baseExchangeRate 당일매매기준환율 {@code tdt_sby_bse_xcg_rt}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbBalanceSummary(
        @JsonProperty("tot_aet_amt") String totalAssetAmount,
        @JsonProperty("eal_amt_sum") String evalAmountSum,
        @JsonProperty("eal_pls_sum_amt") String profitLossSum,
        @JsonProperty("krw_pft_rt") String krwProfitRate,
        @JsonProperty("tdt_sby_bse_xcg_rt") String baseExchangeRate
    ) {
    }

    /** 해외 종목별 보유 (Output_1). 금액은 외화 기준(fc_*)을 쓴다 — 통화는 요청의 cur_cd 다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbHolding(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("oss_iem_eng_nm") String nameEn,
        @JsonProperty("cns_bse_bnc_qty") String quantity,
        @JsonProperty("fc_avg_phs_pr") String avgPrice,
        @JsonProperty("fc_sec_end_pr") String currentPrice,
        @JsonProperty("fc_eal_amt") String evalAmount,
        @JsonProperty("fc_eal_pls_amt") String profitLoss,
        @JsonProperty("krw_eal_amt") String evalAmountKrw
    ) {
    }

    /**
     * 증권사 무관 보유 목록 — 화면이 국내·해외를 같은 모양으로 그린다.
     *
     * <p>나무는 국내와 해외가 <b>엔드포인트도 필드명도 다르다.</b> 그 차이를 서비스가 흡수해
     * 여기까지만 오면 한 가지 모양이다.
     */
    public record Holdings(
        String accountNo,
        String currency,
        String totalEvalAmount,
        String totalProfitLoss,
        String profitRate,
        List<HoldingItem> items
    ) {
        public static Holdings empty(String accountNo, String currency) {
            return new Holdings(accountNo, currency, "0", "0", "0", List.of());
        }
    }

    /** 보유 종목 1건. */
    public record HoldingItem(
        String symbol,
        String name,
        String quantity,
        String avgPrice,
        String currentPrice,
        String evalAmount,
        String profitLoss
    ) {
    }
}
