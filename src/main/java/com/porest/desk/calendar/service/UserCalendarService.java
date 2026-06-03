package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.calendar.type.CalendarRole;

import java.util.List;

public interface UserCalendarService {
    UserCalendarServiceDto.CalendarInfo createCalendar(UserCalendarServiceDto.CreateCommand command);
    List<UserCalendarServiceDto.CalendarInfo> getCalendars(Long userRowId);
    UserCalendarServiceDto.CalendarInfo updateCalendar(Long calendarId, Long userRowId, UserCalendarServiceDto.UpdateCommand command);
    UserCalendarServiceDto.CalendarInfo toggleVisibility(Long calendarId, Long userRowId);
    void deleteCalendar(Long calendarId, Long userRowId);
    UserCalendarServiceDto.CalendarInfo getOrCreateDefault(Long userRowId);

    // ── 공유 ──
    List<UserCalendarServiceDto.MemberInfo> getMembers(Long calendarId, Long userRowId);
    String regenerateInviteCode(Long calendarId, Long userRowId);
    UserCalendarServiceDto.CalendarInfo joinByInviteCode(Long userRowId, String inviteCode);
    void removeMember(Long calendarId, Long memberId, Long requestUserRowId);
    void changeMemberRole(Long calendarId, Long memberId, CalendarRole permission, Long requestUserRowId);
}
