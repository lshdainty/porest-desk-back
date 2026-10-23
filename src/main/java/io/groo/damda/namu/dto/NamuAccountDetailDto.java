package io.groo.damda.namu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 나무증권 "계좌 자세히" 응답 (13b-0 · N5-나) — 계좌 자산 · 실현손익 · 권리(배당).
 *
 * <p>모든 칸은 null 허용이고 숫자는 나무가 주는 문자열 그대로다. 열 때만 불러 본인 키 호출을 줄인다.
 */
public final class NamuAccountDetailDto {

    private NamuAccountDetailDto() {
    }

    // === 응답 (우리 API) ===

    /** 계좌 자산(원화) — {@code assetStatus}. */
    @Schema(name = "NamuAssetStatus")
    public record AssetStatus(String totalAssets, String netAssets, String deposit,
                              String depositD2, String withdrawable) {
    }

    /**
     * 실현손익. 한쪽 조회가 실패하면 그쪽({@code kr*} 또는 {@code us*})만 null 이다 — 다른 쪽은 남긴다.
     * 미국 줄에는 종목 이름이 없다(문서) → 날짜별 줄.
     *
     * @param truncated 연속조회가 최대 쪽 수에서 끊겼다 — 줄 · 합계가 덜 셌을 수 있다(화면이 알려야 한다)
     */
    @Schema(name = "NamuRealizedPnl")
    public record RealizedPnl(String krTotal, List<KrPnlItem> krItems,
                              String usTotal, List<UsPnlRow> usRows, boolean truncated) {
    }

    @Schema(name = "NamuKrPnlItem")
    public record KrPnlItem(String symbol, String name, String sellQuantity, String profit, String rate) {
    }

    @Schema(name = "NamuUsPnlRow")
    public record UsPnlRow(String date, String sellQuantity, String profit, String rate) {
    }

    /** 권리 한 줄. {@code typeName} 은 예정만, {@code amount} · {@code payDate} 는 지난 것만 채워진다. */
    @Schema(name = "NamuRight")
    public record Right(String symbol, String name, String typeCode, String typeName, String baseDate,
                        String quantity, String amount, String payDate) {
    }

    // === 나무 응답 (상류) ===

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssetStatusRaw(
        @JsonProperty("tot_aet_amt") String totalAssets,
        @JsonProperty("nas_amt") String netAssets,
        @JsonProperty("dca") String deposit,
        @JsonProperty("nxt2_dd_dca") String depositD2,
        @JsonProperty("drn_pbl_amt") String withdrawable
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrPnlSummary(@JsonProperty("pls_amt") String profit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrPnlRaw(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("sll_qty") String sellQuantity,
        @JsonProperty("pls_amt") String profit,
        @JsonProperty("pft_rt") String rate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsPnlSummary(@JsonProperty("fc_rzt_pls_sum") String profit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsPnlRaw(
        @JsonProperty("orr_dt") String date,
        @JsonProperty("sll_qty") String sellQuantity,
        @JsonProperty("fc_rzt_pls") String profit,
        @JsonProperty("fc_rzt_pft_rt") String rate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RightScheduledRaw(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("rit_tp_cd") String typeCode,
        @JsonProperty("rit_tp_nm") String typeName,
        @JsonProperty("bse_dt") String baseDate,
        @JsonProperty("aloc_qty") String quantity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RightHeldRaw(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("rit_tp_cd") String typeCode,
        @JsonProperty("bse_dt") String baseDate,
        @JsonProperty("hld_qty") String quantity,
        @JsonProperty("rit_aloc_amt") String amount,
        @JsonProperty("aloc_amt_pym_dt") String payDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RightHeldSummary(@JsonProperty("sta_dt") String startDate) {
    }
}
