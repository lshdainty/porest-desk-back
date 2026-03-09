package com.porest.desk.calendar.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.calendar.type.HolidayType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class HolidayApiDto {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateRequest(
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(
        Long rowId,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(HolidayServiceDto.HolidayInfo info) {
            return new Response(
                info.rowId(),
                info.holidayDate(),
                info.holidayName(),
                info.holidayType(),
                info.isRecurring(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ListResponse(
        List<Response> holidays
    ) {
        public static ListResponse from(List<HolidayServiceDto.HolidayInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
