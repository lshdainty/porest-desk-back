package com.porest.desk.todo.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.patch.Patch;
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
        List<Long> tagIds,
        TodoType type
    ) {}

    /**
     * 수정 명령 — 각 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}).
     * {@code tagIds} 만 종전 그대로다({@code null}=미변경, 빈 목록=전부 해제).
     */
    public record UpdateCommand(
        Patch<String> title,
        Patch<String> content,
        Patch<TodoPriority> priority,
        Patch<String> category,
        Patch<LocalDate> dueDate,
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
        List<TagInfo> tags,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        /** 이번 요청(상태 토글)으로 실제 적립된 별빛 — 조회·그 외 경로는 0. 화면 "+N" 토스트 근거. */
        int earnedStarlight
    ) {
        public static TodoInfo from(Todo todo) {
            return from(todo, List.of());
        }

        public static TodoInfo from(Todo todo, List<TagInfo> tags) {
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
                tags,
                todo.getCreateAt(),
                todo.getModifyAt(),
                0
            );
        }

        public TodoInfo withEarnedStarlight(int earned) {
            return new TodoInfo(rowId, userRowId, type, title, content, priority, category, status,
                dueDate, completedAt, sortOrder, isPinned, tags, createAt, modifyAt, earned);
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
