package com.porest.desk.stock.type;

/**
 * 종목 유형. 파일마다 다른 원본 구분값을 4종으로 정규화한다.
 *
 * <p>ETN·ETC 는 KIS 해외파일이 ETP(3)로 묶는 기준을 따라 ETF 로 정규화한다.
 */
public enum StockSecurityType {
    STOCK,
    ETF,
    INDEX,
    WARRANT
}
