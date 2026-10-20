package io.groo.damda.calendar.service;

import io.groo.damda.calendar.service.dto.CalendarAggregateDto;

import java.time.LocalDate;

public interface CalendarAggregateService {
    CalendarAggregateDto.AggregateData getAggregateData(Long userRowId, LocalDate startDate, LocalDate endDate);
}
