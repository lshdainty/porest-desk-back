package com.porest.desk.stock.repository;

import com.porest.desk.stock.type.StockSecurityType;

/**
 * 종목 검색 조건.
 *
 * @param keyword      한글명·영문명·심볼 부분 일치 검색어
 * @param countryCode  국가 필터 (KR, US, CN, JP, HK, VN)
 * @param securityType 종목 유형 필터
 */
public record StockMasterSearchCondition(
    String keyword,
    String countryCode,
    StockSecurityType securityType
) {
}
