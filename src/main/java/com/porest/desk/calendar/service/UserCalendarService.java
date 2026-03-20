package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;

import java.util.List;

public interface UserCalendarService {
    UserCalendarServiceDto.CalendarInfo createCalendar(UserCalendarServiceDto.CreateCommand command);
    List<UserCalendarServiceDto.CalendarInfo> getCalendars(Long userRowId);
    UserCalendarServiceDto.CalendarInfo updateCalendar(Long calendarId, Long userRowId, UserCalendarServiceDto.UpdateCommand command);
    UserCalendarServiceDto.CalendarInfo toggleVisibility(Long calendarId, Long userRowId);
    void deleteCalendar(Long calendarId, Long userRowId);
    UserCalendarServiceDto.CalendarInfo getOrCreateDefault(Long userRowId);
}
