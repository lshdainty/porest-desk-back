package com.porest.desk.calendar.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.service.dto.EventReminderServiceDto;
import com.porest.desk.calendar.type.CalendarEventType;

import java.time.LocalDateTime;
import java.util.List;

public class CalendarEventApiDto {

    public record CreateRequest(
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String location,
        String rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId,
        Long groupRowId
    ) {}

    public record UpdateRequest(
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String location,
        String rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId,
        Long groupRowId
    ) {}

    public record ReminderResponse(
        Long rowId,
        Long eventRowId,
        String reminderType,
        Integer minutesBefore,
        YNType isSent
    ) {
        public static ReminderResponse from(EventReminderServiceDto.ReminderInfo info) {
            return new ReminderResponse(
                info.rowId(),
                info.eventRowId(),
                info.reminderType(),
                info.minutesBefore(),
                info.isSent()
            );
        }
    }

    public record Response(
        Long rowId,
        Long userRowId,
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String labelName,
        String labelColor,
        String location,
        String rrule,
        Long recurrenceId,
        YNType isException,
        List<ReminderResponse> reminders,
        Long calendarRowId,
        String calendarName,
        String calendarColor,
        Long groupRowId,
        String groupName,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(CalendarEventServiceDto.EventInfo info) {
            List<ReminderResponse> reminderResponses = info.reminders() != null
                ? info.reminders().stream().map(ReminderResponse::from).toList()
                : List.of();
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.description(),
                info.eventType(),
                info.color(),
                info.startDate(),
                info.endDate(),
                info.isAllDay(),
                info.labelRowId(),
                info.labelName(),
                info.labelColor(),
                info.location(),
                info.rrule(),
                info.recurrenceId(),
                info.isException(),
                reminderResponses,
                info.calendarRowId(),
                info.calendarName(),
                info.calendarColor(),
                info.groupRowId(),
                info.groupName(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> events
    ) {
        public static ListResponse from(List<CalendarEventServiceDto.EventInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
