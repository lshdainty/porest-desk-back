package io.groo.damda.card.service;

import io.groo.damda.card.service.dto.CardPerformanceServiceDto;

public interface CardPerformanceService {
    CardPerformanceServiceDto.PerformanceInfo getPerformance(CardPerformanceServiceDto.PerformanceQuery query);
}
