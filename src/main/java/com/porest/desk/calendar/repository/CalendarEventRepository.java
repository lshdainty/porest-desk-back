package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository {
    Optional<CalendarEvent> findById(Long rowId);
    List<CalendarEvent> findByUserAndDateRange(Long userRowId, LocalDateTime startDate, LocalDateTime endDate);
    List<CalendarEvent> findByCalendarId(Long calendarRowId);
    CalendarEvent save(CalendarEvent calendarEvent);
}
