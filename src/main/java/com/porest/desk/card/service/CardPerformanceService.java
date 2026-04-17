package com.porest.desk.card.service;

import com.porest.desk.card.service.dto.CardPerformanceServiceDto;

public interface CardPerformanceService {
    CardPerformanceServiceDto.PerformanceInfo getPerformance(CardPerformanceServiceDto.PerformanceQuery query);
}
