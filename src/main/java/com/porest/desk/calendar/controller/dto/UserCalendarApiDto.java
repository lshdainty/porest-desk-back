package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;

import java.util.List;

public class UserCalendarApiDto {

    public record CreateRequest(
        String calendarName,
        String color
    ) {}

    public record UpdateRequest(
        String calendarName,
        String color
    ) {}

    public record Response(
        Long rowId,
        String calendarName,
        String color,
        Integer sortOrder,
        boolean isDefault,
        boolean isVisible
    ) {
        public static Response from(UserCalendarServiceDto.CalendarInfo info) {
            return new Response(
                info.rowId(),
                info.calendarName(),
                info.color(),
                info.sortOrder(),
                info.isDefault(),
                info.isVisible()
            );
        }
    }

    public record ListResponse(
        List<Response> calendars
    ) {
        public static ListResponse from(List<UserCalendarServiceDto.CalendarInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
