package com.porest.desk.todo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TodoApiDto {

    @Schema(name = "TodoCreateRequest")
    public record CreateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있습니다")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "메모는 10,000자까지 입력할 수 있습니다")
        String content,
        TodoPriority priority,
        @Size(max = FieldLimits.LABEL_MAX, message = "카테고리는 50자까지 입력할 수 있습니다")
        String category,
        LocalDate dueDate,
        Long parentRowId,
        List<Long> tagIds,
        TodoType type
    ) {}

    @Schema(name = "TodoUpdateRequest")
    public record UpdateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있습니다")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "메모는 10,000자까지 입력할 수 있습니다")
        String content,
        TodoPriority priority,
        @Size(max = FieldLimits.LABEL_MAX, message = "카테고리는 50자까지 입력할 수 있습니다")
        String category,
        LocalDate dueDate,
        List<Long> tagIds
    ) {}

    @Schema(name = "TodoReorderRequest")
    public record ReorderRequest(
        List<ReorderItem> items
    ) {
        @Schema(name = "TodoReorderItem")
        public record ReorderItem(
            Long todoId,
            int sortOrder
        ) {}
    }

    public record TagUpdateRequest(
        List<Long> tagIds
    ) {}

    @Schema(name = "TodoResponse")
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
        Long parentRowId,
        List<TagResponse> tags,
        int subtaskCount,
        int subtaskCompletedCount,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        /** 이번 요청(상태 토글)으로 실제 적립된 별빛 — 그 외 응답은 0. */
        int earnedStarlight
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
                info.parentRowId(),
                info.tags() != null ? info.tags().stream().map(TagResponse::from).toList() : List.of(),
                info.subtaskCount(),
                info.subtaskCompletedCount(),
                info.createAt(),
                info.modifyAt(),
                info.earnedStarlight()
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

    @Schema(name = "TodoListResponse")
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
