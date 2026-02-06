package com.porest.desk.calendar.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
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
        YNType isAllDay
    ) {}

    public record UpdateRequest(
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay
    ) {}

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
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(CalendarEventServiceDto.EventInfo info) {
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
