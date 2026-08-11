package com.porest.desk.todo.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TodoServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate,
        Long parentRowId,
        List<Long> tagIds,
        TodoType type
    ) {}

    public record UpdateCommand(
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate,
        List<Long> tagIds
    ) {}

    public record ReorderCommand(
        List<ReorderItem> items
    ) {
        public record ReorderItem(
            Long todoId,
            int sortOrder
        ) {}
    }

    public record TodoInfo(
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
        List<TagInfo> tags,
        int subtaskCount,
        int subtaskCompletedCount,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        /** 이번 요청(상태 토글)으로 실제 적립된 별빛 — 조회·그 외 경로는 0. 화면 "+N" 토스트 근거. */
        int earnedStarlight
    ) {
        public static TodoInfo from(Todo todo) {
            return from(todo, List.of(), 0, 0);
        }

        public static TodoInfo from(Todo todo, List<TagInfo> tags, int subtaskCount, int subtaskCompletedCount) {
            return new TodoInfo(
                todo.getRowId(),
                todo.getUser().getRowId(),
                todo.getType(),
                todo.getTitle(),
                todo.getContent(),
                todo.getPriority(),
                todo.getCategory(),
                todo.getStatus(),
                todo.getDueDate(),
                todo.getCompletedAt(),
                todo.getSortOrder(),
                todo.getIsPinned(),
                todo.getParent() != null ? todo.getParent().getRowId() : null,
                tags,
                subtaskCount,
                subtaskCompletedCount,
                todo.getCreateAt(),
                todo.getModifyAt(),
                0
            );
        }

        public TodoInfo withEarnedStarlight(int earned) {
            return new TodoInfo(rowId, userRowId, type, title, content, priority, category, status,
                dueDate, completedAt, sortOrder, isPinned, parentRowId, tags,
                subtaskCount, subtaskCompletedCount, createAt, modifyAt, earned);
        }
    }

    public record TagInfo(
        Long rowId,
        String tagName,
        String color
    ) {}

    public record TodoStats(
        long totalCount,
        long pendingCount,
        long inProgressCount,
        long completedCount,
        long todayDueCount,
        long overDueCount,
        long noteCount
    ) {}
}
