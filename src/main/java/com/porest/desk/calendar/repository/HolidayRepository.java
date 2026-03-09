package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.Holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository {
    Optional<Holiday> findById(Long rowId);
    List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate);
    List<Holiday> findAllRecurring();
    Holiday save(Holiday holiday);
}
