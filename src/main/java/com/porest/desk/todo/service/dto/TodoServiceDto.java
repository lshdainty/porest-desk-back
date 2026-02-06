package com.porest.desk.todo.service.dto;

import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;

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
        LocalDate dueDate
    ) {}

    public record UpdateCommand(
        String title,
        String content,
        TodoPriority priority,
        String category,
        LocalDate dueDate
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
        public static TodoInfo from(Todo todo) {
            return new TodoInfo(
                todo.getRowId(),
                todo.getUser().getRowId(),
                todo.getTitle(),
                todo.getContent(),
                todo.getPriority(),
                todo.getCategory(),
                todo.getStatus(),
                todo.getDueDate(),
                todo.getCompletedAt(),
                todo.getSortOrder(),
                todo.getCreateAt(),
                todo.getModifyAt()
            );
        }
    }
}
