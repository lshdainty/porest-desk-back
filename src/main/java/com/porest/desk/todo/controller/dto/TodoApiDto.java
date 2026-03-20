package com.porest.desk.todo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
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
        LocalDate dueDate,
        Long projectRowId,
        Long parentRowId,
        List<Long> tagIds,
        TodoType type
    ) {}

    public record UpdateRequest(
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate,
        Long projectRowId,
        List<Long> tagIds
    ) {}

    public record ReorderRequest(
        List<ReorderItem> items
    ) {
        public record ReorderItem(
            Long todoId,
            int sortOrder
        ) {}
    }

    public record TagUpdateRequest(
        List<Long> tagIds
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        TodoType type,
        String title,
        String content,
        TodoPriority priority,
        String category,
        TodoStatus status,
        LocalDate dueDate,
        LocalDateTime completedAt,
        Integer sortOrder,
        YNType isPinned,
        Long projectRowId,
        String projectName,
        Long parentRowId,
        List<TagResponse> tags,
        int subtaskCount,
        int subtaskCompletedCount,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(TodoServiceDto.TodoInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.type(),
                info.title(),
                info.content(),
                info.priority(),
                info.category(),
                info.status(),
                info.dueDate(),
                info.completedAt(),
                info.sortOrder(),
                info.isPinned(),
                info.projectRowId(),
                info.projectName(),
                info.parentRowId(),
                info.tags() != null ? info.tags().stream().map(TagResponse::from).toList() : List.of(),
                info.subtaskCount(),
                info.subtaskCompletedCount(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record TagResponse(
        Long rowId,
        String tagName,
        String color
    ) {
        public static TagResponse from(TodoServiceDto.TagInfo info) {
            return new TagResponse(info.rowId(), info.tagName(), info.color());
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

    public record StatsResponse(
        long totalCount,
        long pendingCount,
        long inProgressCount,
        long completedCount,
        long todayDueCount,
        long overDueCount,
        long noteCount
    ) {
        public static StatsResponse from(TodoServiceDto.TodoStats stats) {
            return new StatsResponse(
                stats.totalCount(),
                stats.pendingCount(),
                stats.inProgressCount(),
                stats.completedCount(),
                stats.todayDueCount(),
                stats.overDueCount(),
                stats.noteCount()
            );
        }
    }
}
