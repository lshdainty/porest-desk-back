package com.porest.desk.calendar.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.service.dto.EventReminderServiceDto;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.validation.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class CalendarEventApiDto {

    @Schema(name = "CalendarEventCreateRequest")
    public record CreateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있습니다")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "설명은 10,000자까지 입력할 수 있습니다")
        String description,
        CalendarEventType eventType,
        // calendar_event.color 는 varchar(20).
        @Size(max = 20, message = "색상 값이 너무 깁니다")
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "장소는 500자까지 입력할 수 있습니다")
        String location,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "반복 규칙이 너무 깁니다")
        String rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId
    ) {}

    @Schema(name = "CalendarEventUpdateRequest")
    public record UpdateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있습니다")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "설명은 10,000자까지 입력할 수 있습니다")
        String description,
        CalendarEventType eventType,
        // calendar_event.color 는 varchar(20).
        @Size(max = 20, message = "색상 값이 너무 깁니다")
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "장소는 500자까지 입력할 수 있습니다")
        String location,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "반복 규칙이 너무 깁니다")
        String rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId
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

    @Schema(name = "CalendarEventResponse")
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
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @Schema(name = "CalendarEventListResponse")
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
