package com.porest.desk.todo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TodoApiDto {

    /**
     * {@code todo.title} · {@code todo.priority} 는 둘 다 NOT NULL 인데 답이 다르다.
     *
     * <p><b>제목은 필수로 건다.</b> 제목 없는 할 일은 화면에도 뜻이 없고, 웹·앱 모두 빈 제목을
     * 이미 막고 있다. 종전엔 빠뜨리면 409 "다른 곳에서 먼저 수정됐어요" 였다(QA #81).
     *
     * <p><b>중요도는 필수로 걸지 않는다.</b> 앱의 하위 할 일 빠른 추가가 제목만 보낸다
     * ({@code todo_edit_dialog.dart} 의 {@code repo.create(title: title)}) — 여기에
     * {@code @NotNull} 을 걸면 그 화면이 <b>지금 되던 것도 못 하게</b> 400 을 맞는다.
     * 값이 없을 때의 답은 거절이 아니라 기본값이고, 그 기본값은 서비스가 씌운다
     * ({@code TodoServiceImpl.createTodo}).
     */
    @Schema(name = "TodoCreateRequest")
    public record CreateRequest(
        @NotBlank(message = "할 일 제목을 입력해 주세요")
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "메모는 10,000자까지 입력할 수 있어요")
        String content,
        TodoPriority priority,
        @Size(max = FieldLimits.LABEL_MAX, message = "카테고리는 50자까지 입력할 수 있어요")
        String category,
        LocalDate dueDate,
        Long parentRowId,
        List<Long> tagIds,
        TodoType type
    ) {}

    @Schema(name = "TodoUpdateRequest")
    public record UpdateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "메모는 10,000자까지 입력할 수 있어요")
        String content,
        TodoPriority priority,
        @Size(max = FieldLimits.LABEL_MAX, message = "카테고리는 50자까지 입력할 수 있어요")
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
