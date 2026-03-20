package com.porest.desk.todo.controller.dto;

import com.porest.desk.todo.service.dto.TodoTagServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class TodoTagApiDto {

    public record CreateRequest(
        String tagName,
        String color
    ) {}

    public record UpdateRequest(
        String tagName,
        String color
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        String tagName,
        String color,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(TodoTagServiceDto.TagInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.tagName(),
                info.color(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> tags
    ) {
        public static ListResponse from(List<TodoTagServiceDto.TagInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
