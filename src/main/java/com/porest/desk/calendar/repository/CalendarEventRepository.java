package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository {
    Optional<CalendarEvent> findById(Long rowId);
    List<CalendarEvent> findByUserAndDateRange(Long userRowId, LocalDateTime startDate, LocalDateTime endDate);
    List<CalendarEvent> findByCalendarId(Long calendarRowId);
    /** 접근 가능한 캘린더(소유+공유받음) 집합의 이벤트를 기간으로 조회. */
    List<CalendarEvent> findByCalendarIdsAndDateRange(List<Long> calendarRowIds, LocalDateTime startDate, LocalDateTime endDate);
    CalendarEvent save(CalendarEvent calendarEvent);
}
