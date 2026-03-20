package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.CalendarAggregateDto;

import java.time.LocalDate;

public interface CalendarAggregateService {
    CalendarAggregateDto.AggregateData getAggregateData(Long userRowId, LocalDate startDate, LocalDate endDate);
}
