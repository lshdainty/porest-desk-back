package com.porest.desk.toss.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스증권 Market Info(환율·장 운영 일정) 응답 DTO 모음.<br>
 * 휴장 세션은 모두 null 로 내려오므로 nested 필드는 nullable 로 둔다.
 */
public final class TossMarketInfoDto {

    private TossMarketInfoDto() {
    }

    /** 환율 조회 응답 (KRW↔USD, 참고용 표시 환율) */
    @Schema(name = "TossExchangeRateResponse")
    public record ExchangeRateResponse(
            String baseCurrency,
            String quoteCurrency,
            String rate,
            String midRate,
            String basisPoint,
            String rateChangeType,
            String validFrom,
            String validUntil
    ) {
    }

    // === 국내(KR) 장 운영 일정 ===

    /** 국내 장 운영 정보 응답 */
    public record KrMarketCalendarResponse(
            KrMarketDay today,
            KrMarketDay previousBusinessDay,
            KrMarketDay nextBusinessDay
    ) {
    }

    /** 국내 영업일 정보. 둘 다 휴장이면 integrated 가 null */
    public record KrMarketDay(
            String date,
            IntegratedHour integrated
    ) {
    }

    /** 거래 가능 시간 (통합 모드 KRX+NXT). 세 세션 각각 nullable */
    public record IntegratedHour(
            PreMarketSession preMarket,
            RegularMarketSession regularMarket,
            AfterMarketSession afterMarket
    ) {
    }

    /** 프리마켓 세션 (NXT 접속매매) */
    public record PreMarketSession(
            String startTime,
            String singlePriceAuctionStartTime,
            String endTime
    ) {
    }

    /** 정규장 세션 (KRX·NXT 합집합) */
    public record RegularMarketSession(
            String startTime,
            String singlePriceAuctionStartTime,
            String endTime
    ) {
    }

    /** 애프터마켓 세션 (NXT) */
    public record AfterMarketSession(
            String startTime,
            String singlePriceAuctionEndTime,
            String endTime
    ) {
    }

    // === 미국(US) 장 운영 일정 ===

    /** 해외(미국) 장 운영 정보 응답 */
    public record UsMarketCalendarResponse(
            UsMarketDay today,
            UsMarketDay previousBusinessDay,
            UsMarketDay nextBusinessDay
    ) {
    }

    /** 미국 영업일 정보. 4 세션 각각 nullable (휴장일이면 모두 null) */
    public record UsMarketDay(
            String date,
            UsSession dayMarket,
            UsSession preMarket,
            UsSession regularMarket,
            UsSession afterMarket
    ) {
    }

    /** 미국 시장 세션 (시작/종료 시각). 4 세션 모두 동일 구조 */
    public record UsSession(
            String startTime,
            String endTime
    ) {
    }
}
