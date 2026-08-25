package com.porest.desk.todo.controller.dto;

import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class TodoTagApiDto {

    @Schema(name = "TodoTagCreateRequest")
    public record CreateRequest(
        String tagName,
        String color
    ) {}

    @Schema(name = "TodoTagUpdateRequest")
    public record UpdateRequest(
        String tagName,
        String color
    ) {}

    @Schema(name = "TodoTagResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String tagName,
        String color,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        long usageCount
    ) {
        public static Response from(TodoTagServiceDto.TagInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.tagName(),
                info.color(),
                info.createAt(),
                info.modifyAt(),
                info.usageCount()
            );
        }
    }

    @Schema(name = "TodoTagListResponse")
    public record ListResponse(
        List<Response> tags
    ) {
        public static ListResponse from(List<TodoTagServiceDto.TagInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
