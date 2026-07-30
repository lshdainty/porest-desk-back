package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.calendar.type.HolidayType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class HolidayApiDto {

    public record Response(
        Long rowId,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(HolidayServiceDto.HolidayInfo info) {
            return new Response(
                info.rowId(),
                info.holidayDate(),
                info.holidayName(),
                info.holidayType(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

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
