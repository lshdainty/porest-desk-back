package com.porest.desk.toss.dto;

/**
 * 토스증권 Stock Info(종목 기본정보·유의사항) 응답 DTO 모음.
 */
public final class TossStockDto {

    private TossStockDto() {
    }

    /** 종목 기본 정보 */
    public record StockInfo(
            String symbol,
            String name,
            String englishName,
            String isinCode,
            String market,
            String securityType,
            Boolean isCommonShare,
            String status,
            String currency,
            String listDate,
            String delistDate,
            String sharesOutstanding,
            String leverageFactor,
            KrMarketDetail koreanMarketDetail
    ) {
    }

    /** 국내 종목 전용 상세. 국내 종목(KOSPI/KOSDAQ/KR_ETC)에만 존재 */
    public record KrMarketDetail(
            Boolean liquidationTrading,
            Boolean nxtSupported,
            Boolean krxTradingSuspended,
            Boolean nxtTradingSuspended
    ) {
    }

    /** 매수 유의사항 (정리매매·단기과열·투자경고/위험·VI 등). unknown warningType 허용 */
    public record StockWarning(
            String warningType,
            String exchange,
            String startDate,
            String endDate
    ) {
    }
}
