package com.porest.desk.card.service.dto;

import java.time.YearMonth;

public class CardPerformanceServiceDto {

    public record PerformanceQuery(Long userRowId, Long assetRowId, YearMonth yearMonth) {}

    public record PerformanceInfo(
        Long assetRowId,
        YearMonth yearMonth,
        Integer requiredAmount,
        String requiredText,
        boolean isRequired,
        long currentAmount,
        double achievementRate,
        boolean isAchieved,
        Long remainingAmount
    ) {
        public static PerformanceInfo notApplicable(Long assetRowId, YearMonth yearMonth) {
            return new PerformanceInfo(assetRowId, yearMonth, 0, null, false, 0L, 0.0, true, 0L);
        }
    }
}
