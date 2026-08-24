package com.porest.desk.namu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 나무증권 시세 응답. 국내와 해외가 <b>필드명부터 다르다</b> — 하나로 합치지 않고
 * 각자 두고 서비스에서 공통 모양으로 옮긴다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 를 붙인 이유 — 응답 필드가 수십 개인데
 * 우리가 쓰는 건 몇 개뿐이다. 나무가 필드를 추가할 때마다 역직렬화가 깨지면 안 된다.
 */
public final class NamuMarketDto {

    private NamuMarketDto() {
    }

    /**
     * 국내주식 현재가 ({@code POST /krstock/quote/v1/currentPrice}).
     *
     * @param price        현재가 {@code stck_prpr}
     * @param changeSign   전일대비 부호 {@code prdy_vrss_sign}
     * @param change       전일대비 {@code prdy_vrss}
     * @param changeRate   등락률(%) {@code prdy_ctrt}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrPrice(
        @JsonProperty("stck_prpr") String price,
        @JsonProperty("prdy_vrss_sign") String changeSign,
        @JsonProperty("prdy_vrss") String change,
        @JsonProperty("prdy_ctrt") String changeRate
    ) {
    }

    /**
     * 해외주식 현재가 ({@code POST /gbstock/quote/v1/current}).
     *
     * @param price      현재가 {@code trdprc}
     * @param changeSign 전일대비 부호 {@code netchng_cls}
     * @param change     전일대비 {@code netchng}
     * @param changeRate 등락률(%) {@code pctchng}
     * @param currency   거래 통화 {@code currency_unit}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbPrice(
        @JsonProperty("trdprc") String price,
        @JsonProperty("netchng_cls") String changeSign,
        @JsonProperty("netchng") String change,
        @JsonProperty("pctchng") String changeRate,
        @JsonProperty("currency_unit") String currency
    ) {
    }
}
