package com.porest.desk.stock.client.dto;

import com.porest.desk.stock.type.StockSecurityType;

/**
 * 마스터파일 1행을 정규화한 종목 레코드.
 *
 * @param symbol         종목 코드 (국내 단축코드, 해외 티커, 업종지수 코드)
 * @param standardCode   표준 코드 (ISIN). 국내 주식만 존재
 * @param realtimeSymbol KIS 실시간 시세용 심볼 (예: NASAAPL). 해외만 존재
 * @param nameKr         한글 종목명
 * @param nameEn         영문 종목명. 해외만 존재
 * @param securityType   정규화된 종목 유형
 * @param currency       거래 통화
 */
public record KisStockRecord(
    String symbol,
    String standardCode,
    String realtimeSymbol,
    String nameKr,
    String nameEn,
    StockSecurityType securityType,
    String currency
) {
}
