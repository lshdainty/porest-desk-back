package com.porest.desk.asset.type;

/**
 * 투자 보유 항목의 유형. 유형마다 수량 단위와 입력 양식이 다르다 — 주식 주 / 금 g / 코인 개.
 *
 * <p>토스 시세 연동({@code linked=Y})은 {@link #STOCK} 만 가능하다.
 * 토스 Open API 는 국내·미국 주식 시세만 제공하고, KRX 금시장 현물과 코인은 취급하지 않는다.
 */
public enum HoldingType {
    STOCK,
    GOLD,
    CRYPTO
}
