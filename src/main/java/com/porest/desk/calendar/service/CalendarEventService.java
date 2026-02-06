package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarEventService {
    CalendarEventServiceDto.EventInfo createEvent(CalendarEventServiceDto.CreateCommand command);
    List<CalendarEventServiceDto.EventInfo> getEvents(Long userRowId, LocalDateTime startDate, LocalDateTime endDate);
    CalendarEventServiceDto.EventInfo updateEvent(Long eventId, CalendarEventServiceDto.UpdateCommand command);
    void deleteEvent(Long eventId);
}
