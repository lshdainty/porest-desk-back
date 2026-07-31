package com.porest.desk.toss.dto;

import java.util.List;

/**
 * 토스증권 시장 지표(국내 지수·국채) 응답 DTO 모음.<br>
 * 지원 심볼은 토스 카탈로그 8종(KOSPI, KOSDAQ, KR_BOND_2Y~30Y) 뿐이다.
 * 가격은 정밀도 보존을 위해 원본 그대로 String 으로 매핑한다.
 */
public final class TossIndicatorDto {

    private TossIndicatorDto() {
    }

    /** 시장 지표 현재가 (지수: 포인트, 국채: 수익률 %) */
    public record IndicatorPriceResponse(
            String symbol,
            String timestamp,
            String lastPrice
    ) {
    }

    /** 시장 지표 캔들 페이지 응답 */
    public record IndicatorCandlePageResponse(
            List<IndicatorCandle> candles,
            String nextBefore
    ) {
    }

    /** 시장 지표 캔들 한 봉 */
    public record IndicatorCandle(
            String timestamp,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume
    ) {
    }
}
