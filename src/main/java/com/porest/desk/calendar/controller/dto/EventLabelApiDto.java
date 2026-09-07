package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
import com.porest.desk.common.validation.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class EventLabelApiDto {

    /**
     * 생성·수정 모두 이름과 색을 <b>반드시</b> 받는다. {@code event_label.color} 는 NOT NULL 이고
     * 서비스는 받은 값을 그대로 덮는다 — 색을 빼고 보내면 종전엔 저장이 409 "다른 곳에서 먼저
     * 수정됐어요" 로 튕겼다(QA #81). 웹·앱 모두 두 값을 항상 함께 보내므로 필수로 걸어도
     * 쓰던 화면이 막히지 않는다(2026-09-07 양쪽 코드로 확인).
     */
    @Schema(name = "EventLabelCreateRequest")
    public record CreateRequest(
        @NotBlank(message = "라벨 이름을 입력해 주세요")
        @Size(max = FieldLimits.NAME_MAX, message = "라벨 이름은 50자까지 입력할 수 있어요")
        String labelName,
        @NotBlank(message = "라벨 색상을 골라 주세요")
        @Size(max = 20, message = "색상 값이 너무 길어요")
        String color
    ) {}

    @Schema(name = "EventLabelUpdateRequest")
    public record UpdateRequest(
        @NotBlank(message = "라벨 이름을 입력해 주세요")
        @Size(max = FieldLimits.NAME_MAX, message = "라벨 이름은 50자까지 입력할 수 있어요")
        String labelName,
        @NotBlank(message = "라벨 색상을 골라 주세요")
        @Size(max = 20, message = "색상 값이 너무 길어요")
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
