package com.porest.desk.namu.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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

    /**
     * 계좌 1건 ({@code POST /n2/acctinfo} 의 Output_0).
     *
     * <p><b>이 레코드만 응답에도 그대로 실린다</b>({@code GET /api/v1/namu/accounts}). 나머지
     * 나무 응답은 {@link Holdings} 로 한 번 옮겨 담는데 계좌는 그럴 게 없어서 그대로 흐른다.
     *
     * <p>그래서 읽기 전용 별칭({@code @JsonAlias})을 쓴다. {@code @JsonProperty} 는 양방향이라
     * <b>우리 API 도 {@code acct_no}·{@code acct_type} 로 내보냈다</b> — front·app 은 둘 다
     * {@code accountNo}·{@code accountType} 을 읽으므로 계좌번호가 빈 문자열이 됐다(앱키가 아직
     * 없어 드러나지 않았을 뿐이다). 별칭은 역직렬화에만 걸리므로 나무 응답은 그대로 읽고
     * 나가는 이름만 우리 어휘가 된다.
     *
     * <p>{@code usable} 은 <b>나무가 주는 값이 아니라 우리가 채우는 값</b>이다. 계좌구분이
     * 그 계좌를 쓸 수 있는 도메인을 정하므로(운영 01·02 / 모의투자 03), 목록만 보고는 어느 것을
     * 골라야 하는지 알 수 없다. 화면이 고를 수 있게 서버가 미리 표시한다 — 목록 자체는 거르지
     * 않는다(사용자가 자기 계좌를 다 보는 건 맞다).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "NamuAccount")
    public record Account(
        @JsonAlias("acct_no") String accountNo,
        @JsonAlias("acct_type") String accountType,
        @Schema(description = "현재 연동 환경에서 잔고 조회에 쓸 수 있는 계좌인지. 나무 응답이 아니라 서버가 채운다")
        Boolean usable
    ) {

        /**
         * 나무 응답 모양 그대로 만든다({@code usable} 미정).
         *
         * <p>생성자가 아니라 정적 팩토리인 이유 — 레코드에 <b>정규 생성자 말고 다른 생성자</b>를
         * 두면 Jackson 이 어느 쪽으로 역직렬화할지 못 정해 계좌목록 파싱이 통째로 깨진다.
         */
        public static Account of(String accountNo, String accountType) {
            return new Account(accountNo, accountType, null);
        }

        /**
         * {@code Boolean} 인 이유 — 나무 응답에 이 필드가 없다. {@code boolean} 으로 두면
         * 역직렬화가 {@code null} 을 원시타입에 못 넣어 <b>계좌목록 파싱이 통째로 깨진다</b>.
         * 나가는 응답에서는 서비스가 항상 채우므로 null 이 새지 않는다.
         */
        public Account withUsable(boolean usable) {
            return new Account(accountNo, accountType, usable);
        }
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
     * 해외 잔고 요약 (Output_0, 객체).
     *
     * <p><b>외화 기준 필드를 쓴다.</b> 종목별({@link GbHolding})을 {@code fc_*}(외화)로 읽으므로
     * 요약도 같은 기준이어야 한다. 원화 합({@code eal_amt_sum})과 섞으면 화면이 원화 금액에
     * 통화 기호만 USD 로 붙여 보여준다.
     *
     * @param totalAssetAmount 총자산(원화) {@code tot_aet_amt}. 참고용
     * @param evalAmountSum    평가금액 합(외화) {@code fc_eal_amt}
     * @param profitLossSum    평가손익 합(외화) {@code fc_eal_pls_amt}
     * @param profitRate       수익률 {@code pft_rt}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbBalanceSummary(
        @JsonProperty("tot_aet_amt") String totalAssetAmount,
        @JsonProperty("fc_eal_amt") String evalAmountSum,
        @JsonProperty("fc_eal_pls_amt") String profitLossSum,
        @JsonProperty("pft_rt") String profitRate
    ) {
    }

    /**
     * 해외 종목별 보유 (Output_1, 배열). 금액은 외화 기준({@code fc_*})을 쓴다.
     *
     * <p><b>환율이 여기 있다.</b> {@code tdt_sby_bse_xcg_rt}(당일매매기준환율)와
     * {@code cur_cd}(통화)는 스펙상 {@code Output_1} 에만 있다 — 종목마다 통화가 달라
     * 계좌 단위 요약이 환율 하나를 들 수 없는 구조다. 나무엔 환율 전용 조회가 없고
     * 지수·환율 통합 API 의 코드값이 공개 문서에 없어서, 이게 문서화된 유일한 경로다.
     */
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
        @JsonProperty("krw_eal_amt") String evalAmountKrw,
        @JsonProperty("cur_cd") String currency,
        @JsonProperty("tdt_sby_bse_xcg_rt") String baseExchangeRate
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
