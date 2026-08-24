package com.porest.desk.securities.service.dto;

/**
 * 시세를 물을 종목 하나. <b>심볼만으로는 종목이 안 정해진다.</b>
 *
 * <p>NH 소스를 붙이며 시장이 6개 늘어(ASX·GER·LSE·JKT·BTQ·PNK) 같은 티커가 여러 시장에
 * 걸리는 경우가 크게 늘었다 — SPY·IVV·JEPI·SOXL 이 그렇다. 심볼만 넘기면 런던 상장 SPY
 * 시세로 미국 보유분을 평가하는 일이 생긴다.
 *
 * @param marketCode stock_master 기준 시장코드. 모르면 null — 그때는
 *                   {@code StockMasterResolver} 의 우선순위로 하나를 고른다
 * @param symbol     stock_master 기준 종목코드
 */
public record InstrumentRef(String marketCode, String symbol) {

    /** 시장을 모르는 자리(편집 폼이 보낸 값 등). */
    public static InstrumentRef of(String symbol) {
        return new InstrumentRef(null, symbol);
    }

    public static InstrumentRef of(String marketCode, String symbol) {
        return new InstrumentRef(marketCode, symbol);
    }
}
