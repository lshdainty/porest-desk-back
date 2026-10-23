package io.groo.damda.namu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.groo.damda.toss.dto.TossMarketDto;
import io.groo.damda.toss.dto.TossStockDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 나무증권 시세 상세 · 목록 · 체결 · 시간외 · ETF · 증거금 응답 (13b-0).
 *
 * <p><b>응답 레코드의 모든 칸은 null 허용이다.</b> 문서에만 있는 칸이 실제 응답에서 비어 와도
 * 화면이 깨지지 않게 한다 — 서버는 칸이 없으면 null 로 내리고, 화면은 값 없는 줄을 그리지 않는다.
 * 숫자 칸은 나무가 주는 문자열 그대로 내린다(정밀도 보존). 호가 · 체결 · 경고는 토스와 <b>같은
 * 이름</b>의 레코드({@link TossMarketDto} · {@link TossStockDto})를 그대로 써서 화면이 한 부품으로
 * 그린다.
 */
public final class NamuQuoteDto {

    private NamuQuoteDto() {
    }

    // === 응답 (우리 API) ===

    /**
     * 종목 상세 시세 — {@code GET /namu/kr/quote} · {@code /namu/gb/quote}.
     *
     * @param marketState   장 상태 어휘 {@code PRE · REGULAR · AFTER · SINGLE_PRICE · HALTED · BEFORE_OPEN · CLOSED · DAYTIME}. 모르면 null
     * @param marketCap     시가총액 — 국내는 <b>원</b>(문서의 억원을 환산), 미국은 달러
     * @param listedShares  상장 주식 수
     * @param warnings      투자 경고 이름표({@code warningType} 만 채운다). 없으면 빈 목록
     * @param extendedSession 미국 프리 · 애프터 · 주간거래 시세. 그 시간이 아니면 null
     * @param etf           ETF 정보. ETF 가 아니면 null
     */
    @Schema(name = "NamuQuoteDetail")
    public record Detail(
        String symbol,
        String name,
        String currency,
        String price,
        String previousClose,
        String open,
        String high,
        String low,
        String volume,
        String upperLimit,
        String lowerLimit,
        TossMarketDto.OrderbookResponse orderbook,
        List<TossStockDto.StockWarning> warnings,
        String marketState,
        String sector,
        String marketCap,
        String listingDate,
        String listedShares,
        String per,
        String pbr,
        String eps,
        String high52w,
        String low52w,
        String foreignRate,
        ExtendedSession extendedSession,
        Etf etf
    ) {
    }

    /** 미국 시간외 — {@code session} 은 {@code PRE · AFTER · DAYTIME}. */
    @Schema(name = "NamuExtendedSession")
    public record ExtendedSession(String session, String price, String changeRate, String changeSign) {
    }

    /** ETF 정보. {@code netAssets} 는 문서대로 억원. */
    @Schema(name = "NamuEtfInfo")
    public record Etf(
        String nav,
        String navChange,
        String navChangeSign,
        String discountRate,
        String discountSign,
        String trackingError,
        String netAssets,
        String componentCount
    ) {
    }

    /**
     * 목록 시세 — {@code GET /namu/prices}. 공용 {@code PriceQuote}(API 토큰의 공개 응답)에 칸을
     * 더하지 않으려고 나무 전용으로 둔다.
     */
    @Schema(name = "NamuListQuote")
    public record ListQuote(
        String symbol,
        BigDecimal price,
        String currency,
        BigDecimal previousClose,
        String marketState
    ) {
    }

    /** 국내 시간외 단일가 — 가격이 0 이거나 비면 모든 칸 null. */
    @Schema(name = "NamuAfterHours")
    public record AfterHours(String price, String change, String changeRate, String changeSign, String time) {

        public static AfterHours empty() {
            return new AfterHours(null, null, null, null, null);
        }
    }

    /** ETF 구성 종목 한 줄. {@code weight} 는 비중(%). */
    @Schema(name = "NamuEtfComponent")
    public record EtfComponent(String symbol, String name, String weight, String price,
                               String changeRate, String changeSign) {
    }

    /**
     * 해외 증거금(USD 행). <b>이 응답에는 "주문 가능" 칸이 없다</b> — 화면 줄 이름은 "예수금 · 달러"
     * ({@code foreignDeposit}).
     */
    @Schema(name = "NamuMargin")
    public record Margin(String currency, String foreignDeposit, String foreignWithdrawable,
                         String baseExchangeRate) {

        public static Margin empty() {
            return new Margin(null, null, null, null);
        }
    }

    /** 환율 응답 — {@code /securities/exchange-rate} 와 같은 모양. */
    @Schema(name = "NamuExchangeRateResponse")
    public record ExchangeRate(String base, String quote, BigDecimal rate) {
    }

    // === 나무 응답 (상류) ===

    /** 국내 체결 한 줄 ({@code currentExecution} {@code Output_0[]}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrExecutionRow(
        @JsonProperty("bsop_hour") String time,
        @JsonProperty("stck_prpr") String price,
        @JsonProperty("cntg_vol") String volume
    ) {
    }

    /** 해외 체결 한 줄 ({@code executionTrend} {@code Output_0[]}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbExecutionRow(
        @JsonProperty("trade_date") String date,
        @JsonProperty("trade_time") String time,
        @JsonProperty("trdprc") String price,
        @JsonProperty("fill_size") String volume
    ) {
    }

    /** 국내 투자자별 한 줄 ({@code currentInvestor} {@code Output_0[]}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorRow(
        @JsonProperty("bsop_date1") String date,
        @JsonProperty("person") String individual,
        @JsonProperty("frgn_ntby_qty") String foreigner,
        @JsonProperty("gigwan") String institution
    ) {
    }

    /** ETF 구성 종목 한 줄 ({@code etfComponents} {@code Output_0[]}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EtfComponentRow(
        @JsonProperty("iem_cd") String symbol,
        @JsonProperty("iem_nm") String name,
        @JsonProperty("stck_prpr") String price,
        @JsonProperty("prdy_vrss_sign") String changeSign,
        @JsonProperty("prdy_ctrt") String changeRate,
        @JsonProperty("vol") String weight
    ) {
    }

    /** 국내 시간외 단일가 ({@code afterHoursCurrent} {@code Output_0}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AfterHoursRow(
        @JsonProperty("ovtm_untp_prpr") String price,
        @JsonProperty("prdy_vrss_sign") String changeSign,
        @JsonProperty("ovtm_prdy_vrss") String change,
        @JsonProperty("ovtm_prdy_ctrt") String changeRate,
        @JsonProperty("ovtm_cntg_hour") String time
    ) {
    }

    /** 해외 증거금 통화별 한 줄 ({@code margin} {@code Output_0[]}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarginRow(
        @JsonProperty("cur_cd") String currency,
        @JsonProperty("fc_dca") String foreignDeposit,
        @JsonProperty("fc_drn_pbl_amt") String foreignWithdrawable,
        @JsonProperty("sby_bse_xcg_rt") String baseExchangeRate
    ) {
    }
}
