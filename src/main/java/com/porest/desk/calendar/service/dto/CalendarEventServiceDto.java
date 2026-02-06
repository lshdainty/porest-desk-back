package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.type.CalendarEventType;

import java.time.LocalDateTime;

public class CalendarEventServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay
    ) {}

    public record UpdateCommand(
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay
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
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static EventInfo from(CalendarEvent event) {
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
                event.getCreateAt(),
                event.getModifyAt()
            );
        }
    }
}
