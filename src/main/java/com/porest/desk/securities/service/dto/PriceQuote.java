package com.porest.desk.securities.service.dto;

import java.math.BigDecimal;

/**
 * 증권사 무관 현재가. 가계부 자산 평가가 쓰는 최소 단위다.
 *
 * <p>증권사마다 응답 필드명이 다르다(토스 {@code lastPrice}, 나무 국내 {@code stck_prpr},
 * 나무 해외 {@code trdprc}). 그 차이는 {@code SecuritiesPriceProvider} 구현이 흡수하고
 * 여기부터는 한 가지 모양으로 흐른다.
 *
 * @param symbol   stock_master 기준 종목코드
 * @param price    현재가 (거래 통화 기준)
 * @param currency 거래 통화. 원화면 KRW
 */
public record PriceQuote(String symbol, BigDecimal price, String currency) {

    public boolean isKrw() {
        return currency == null || "KRW".equals(currency);
    }
}
