package com.porest.desk.todo.controller.dto;

import com.porest.desk.todo.service.dto.TodoProjectServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class TodoProjectApiDto {

    public record CreateRequest(
        String projectName,
        String description,
        String color,
        String icon
    ) {}

    public record UpdateRequest(
        String projectName,
        String description,
        String color,
        String icon
    ) {}

    public record ReorderRequest(
        List<ReorderItem> items
    ) {
        public record ReorderItem(
            Long projectId,
            int sortOrder
        ) {}
    }

    public record Response(
        Long rowId,
        Long userRowId,
        String projectName,
        String description,
        String color,
        String icon,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(TodoProjectServiceDto.ProjectInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.projectName(),
                info.description(),
                info.color(),
                info.icon(),
                info.sortOrder(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> projects
    ) {
        public static ListResponse from(List<TodoProjectServiceDto.ProjectInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
