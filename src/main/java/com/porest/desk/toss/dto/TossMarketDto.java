package com.porest.desk.toss.dto;

import java.util.List;

/**
 * 토스증권 Market Data(시세) 응답 DTO 모음.<br>
 * 가격·수량은 정밀도 보존을 위해 원본 그대로 String 으로 매핑한다.
 * {@code currency} 등 enum 성 필드는 unknown code 허용을 위해 String 으로 둔다.
 */
public final class TossMarketDto {

    private TossMarketDto() {
    }

    /** 호가 조회 응답 */
    public record OrderbookResponse(
            String timestamp,
            String currency,
            List<OrderbookEntry> asks,
            List<OrderbookEntry> bids
    ) {
    }

    /** 호가 한 단계 (가격/잔량) */
    public record OrderbookEntry(
            String price,
            String volume
    ) {
    }

    /** 현재가 조회 응답 (종목별) */
    public record PriceResponse(
            String symbol,
            String timestamp,
            String lastPrice,
            String currency
    ) {
    }

    /** 최근 체결 내역 한 건 */
    public record Trade(
            String price,
            String volume,
            String timestamp,
            String currency
    ) {
    }

    /** 상/하한가 조회 응답. 미국 주식 등 가격제한이 없는 시장은 limit 가 null */
    public record PriceLimitResponse(
            String timestamp,
            String upperLimitPrice,
            String lowerLimitPrice,
            String currency
    ) {
    }

    /** 캔들 차트 페이지 응답 */
    public record CandlePageResponse(
            List<Candle> candles,
            String nextBefore
    ) {
    }

    /** 캔들 한 봉 */
    public record Candle(
            String timestamp,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume,
            String currency
    ) {
    }
}
