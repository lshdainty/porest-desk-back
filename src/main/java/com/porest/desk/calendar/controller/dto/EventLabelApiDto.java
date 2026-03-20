package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.EventLabelServiceDto;

import java.util.List;

public class EventLabelApiDto {

    public record CreateRequest(
        String labelName,
        String color
    ) {}

    public record UpdateRequest(
        String labelName,
        String color
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String labelName,
        String color,
        Integer sortOrder
    ) {
        public static Response from(EventLabelServiceDto.LabelInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.labelName(),
                info.color(),
                info.sortOrder()
            );
        }
    }

    public record ListResponse(
        List<Response> labels
    ) {
        public static ListResponse from(List<EventLabelServiceDto.LabelInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
