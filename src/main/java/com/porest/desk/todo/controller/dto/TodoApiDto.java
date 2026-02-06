package com.porest.desk.todo.controller.dto;

import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.service.dto.TodoServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TodoApiDto {

    public record CreateRequest(
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate
    ) {}

    public record UpdateRequest(
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate
    ) {}

    public record ReorderRequest(
        List<ReorderItem> items
    ) {
        public record ReorderItem(
            Long todoId,
            int sortOrder
        ) {}
    }

    public record Response(
        Long rowId,
        Long userRowId,
        String title,
        String content,
        TodoPriority priority,
        String category,
        TodoStatus status,
        LocalDate dueDate,
        LocalDateTime completedAt,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(TodoServiceDto.TodoInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.content(),
                info.priority(),
                info.category(),
                info.status(),
                info.dueDate(),
                info.completedAt(),
                info.sortOrder(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> todos
    ) {
        public static ListResponse from(List<TodoServiceDto.TodoInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
