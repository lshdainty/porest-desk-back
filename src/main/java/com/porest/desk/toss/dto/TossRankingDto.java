package com.porest.desk.toss.dto;

import java.util.List;

/**
 * 토스증권 주식 랭킹 응답 DTO 모음.<br>
 * 가격·수량은 정밀도 보존을 위해 원본 그대로 String 으로 매핑한다.
 */
public final class TossRankingDto {

    private TossRankingDto() {
    }

    /** 랭킹 조회 응답 */
    public record RankingResponse(
            String rankedAt,
            List<RankingItem> rankings
    ) {
    }

    /** 랭킹 한 종목 */
    public record RankingItem(
            Integer rank,
            String symbol,
            String currency,
            RankingPrice price,
            String tradingVolume,
            String tradingAmount
    ) {
    }

    /**
     * 랭킹 종목 가격. {@code changeRate} 는 소수 비율(0.0125 = 1.25%)이며,
     * TOP_GAINERS/TOP_LOSERS 는 기간 등락률, 나머지 타입은 전일 대비 등락률이다.
     */
    public record RankingPrice(
            String lastPrice,
            String basePrice,
            String changeRate
    ) {
    }
}
