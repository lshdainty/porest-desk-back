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
        Long projectRowId,
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
        Long projectRowId,
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
        Long projectRowId,
        String projectName,
        Long parentRowId,
        List<TagInfo> tags,
        int subtaskCount,
        int subtaskCompletedCount,
        LocalDateTime createAt,
        LocalDateTime modifyAt
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
                todo.getProject() != null ? todo.getProject().getRowId() : null,
                todo.getProject() != null ? todo.getProject().getProjectName() : null,
                todo.getParent() != null ? todo.getParent().getRowId() : null,
                tags,
                subtaskCount,
                subtaskCompletedCount,
                todo.getCreateAt(),
                todo.getModifyAt()
            );
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
