package com.porest.desk.securities.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

/**
 * 증권사 무관 현재가. 가계부 자산 평가가 쓰는 최소 단위다.
 *
 * <p>증권사마다 응답 필드명이 다르다(토스 {@code lastPrice}, 나무 국내 {@code stck_prpr},
 * 나무 해외 {@code trdprc}). 그 차이는 {@code SecuritiesPriceProvider} 구현이 흡수하고
 * 여기부터는 한 가지 모양으로 흐른다.
 *
 * @param symbol        stock_master 기준 종목코드
 * @param price         현재가 (거래 통화 기준)
 * @param currency      거래 통화. 원화면 KRW
 * @param previousClose 전일 종가 (거래 통화 기준). <b>못 주는 증권사가 있어 null 이 될 수 있다</b> —
 *                      나무는 시세 응답에 전일대비가 딸려 와 공짜로 채우지만, 토스는 캔들을
 *                      종목마다 따로 받아야 해서 여기서는 채우지 않는다. 등락 표시에만 쓰이므로
 *                      없으면 등락을 감추면 되고, 평가액에는 영향이 없다
 */
public record PriceQuote(String symbol, BigDecimal price, String currency, BigDecimal previousClose) {

    /** 전일 종가를 모르는 증권사용. */
    public static PriceQuote of(String symbol, BigDecimal price, String currency) {
        return new PriceQuote(symbol, price, currency, null);
    }

    /**
     * 원화 종목인지 — 환산이 필요한지 가르는 내부 판별.
     *
     * <p>{@code @JsonIgnore} 가 붙은 이유 — Jackson 은 {@code isXxx()} 를 getter 로 잡는다.
     * 이 레코드가 {@code /api/v1/securities/prices} · {@code /api/v1/namu/**} 응답에 그대로
     * 실리므로, 빼 두지 않으면 내부 판별용 불리언이 {@code krw} 필드로 응답과 스펙에 새어 나가고
     * 클라이언트가 그걸 계약으로 읽는다.
     */
    @JsonIgnore
    public boolean isKrw() {
        return currency == null || "KRW".equals(currency);
    }
}
