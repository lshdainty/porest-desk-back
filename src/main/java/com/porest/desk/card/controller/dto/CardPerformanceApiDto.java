package com.porest.desk.card.controller.dto;

import com.porest.desk.card.service.dto.CardPerformanceServiceDto;

public class CardPerformanceApiDto {

    public record PerformanceResponse(
        Long assetRowId,
        String yearMonth,
        Integer requiredAmount,
        String requiredText,
        boolean isRequired,
        long currentAmount,
        double achievementRate,
        boolean isAchieved,
        Long remainingAmount
    ) {
        public static PerformanceResponse from(CardPerformanceServiceDto.PerformanceInfo info) {
            return new PerformanceResponse(
                info.assetRowId(),
                info.yearMonth().toString(),
                info.requiredAmount(),
                info.requiredText(),
                info.isRequired(),
                info.currentAmount(),
                info.achievementRate(),
                info.isAchieved(),
                info.remainingAmount()
            );
        }
    }
}
