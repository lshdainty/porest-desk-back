package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class EventLabelApiDto {

    @Schema(name = "EventLabelCreateRequest")
    public record CreateRequest(
        String labelName,
        String color
    ) {}

    @Schema(name = "EventLabelUpdateRequest")
    public record UpdateRequest(
        String labelName,
        String color
    ) {}

    @Schema(name = "EventLabelResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String labelName,
        String color,
        Integer sortOrder,
        long usageCount
    ) {
        public static Response from(EventLabelServiceDto.LabelInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.labelName(),
                info.color(),
                info.sortOrder(),
                info.usageCount()
            );
        }
    }

    @Schema(name = "EventLabelListResponse")
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
