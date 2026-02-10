package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.type.CalendarEventType;

import java.time.LocalDateTime;
import java.util.List;

public class CalendarEventServiceDto {

    public record CreateCommand(
        Long userRowId,
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
        List<Integer> reminderMinutes
    ) {}

    public record UpdateCommand(
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
        List<Integer> reminderMinutes
    ) {}

    public record EventInfo(
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
        List<EventReminderServiceDto.ReminderInfo> reminders,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static EventInfo from(CalendarEvent event) {
            return from(event, List.of());
        }

        public static EventInfo from(CalendarEvent event, List<EventReminderServiceDto.ReminderInfo> reminders) {
            return new EventInfo(
                event.getRowId(),
                event.getUser().getRowId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getColor(),
                event.getStartDate(),
                event.getEndDate(),
                event.getIsAllDay(),
                event.getLabel() != null ? event.getLabel().getRowId() : null,
                event.getLabel() != null ? event.getLabel().getLabelName() : null,
                event.getLabel() != null ? event.getLabel().getColor() : null,
                event.getLocation(),
                event.getRrule(),
                event.getRecurrenceId(),
                event.getIsException(),
                reminders,
                event.getCreateAt(),
                event.getModifyAt()
            );
        }
    }
}
