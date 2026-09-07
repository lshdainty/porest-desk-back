package com.porest.desk.todo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /**
     * 수정 본문 — <b>실린 칸만 바꾼다</b>(QA #96, 사용자 결정 2026-09-07).
     *
     * <p>{@code Optional} 참조가 {@code null} 이면 키가 없었던 것(유지),
     * {@code Optional.empty()} 면 {@code null} 이 실린 것(지움)이다
     * ({@code AbsentAwareOptionalModule}).
     *
     * <p><b>{@code tagIds} 만 {@code Optional} 이 아니다.</b> 이 칸은 종전부터
     * "{@code null}=미변경 · 빈 배열=전부 해제" 라는 뜻이었고(QA #87 이 그 뜻을 확정했다),
     * 그 계약을 그대로 둔다 — 목록을 통째로 갈아끼우는 칸에서 "지움" 과 "미변경" 을
     * 다시 정의하면 이미 맞춰 둔 화면이 어긋난다.
     */
    @Schema(name = "TodoUpdateRequest")
    public record UpdateRequest(
        Optional<@NotBlank(message = "할 일 제목을 입력해 주세요")
                 @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
                 String> title,
        Optional<@Size(max = FieldLimits.CONTENT_MAX, message = "메모는 10,000자까지 입력할 수 있어요")
                 String> content,
        Optional<TodoPriority> priority,
        Optional<@Size(max = FieldLimits.LABEL_MAX, message = "카테고리는 50자까지 입력할 수 있어요")
                 String> category,
        Optional<LocalDate> dueDate,
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

    /**
     * {@code PATCH /todo/{id}/tags} 의 본문.
     *
     * <h4>키 없음 · null · 빈 배열을 어떻게 가르나</h4>
     * <b>가르지 않는다 — 앞의 둘은 같은 답(400)이라서 가를 필요가 없다.</b> Jackson 은 레코드
     * 생성자 인자에 키가 없을 때도, {@code null} 이 실렸을 때도 똑같이 {@code null} 을 넣는다.
     * 그 둘을 구분하려면 {@code Optional}·{@code JsonNullable} 같은 포장이 필요한데
     * (PUT 의 "없으면 유지 / null 이면 지움" 은 그 구분이 정말 필요해서 그렇게 간다),
     * 여기서는 <b>둘 다 거절</b>이므로 {@code @NotNull} 하나로 끝난다.
     *
     * <p>가르는 자리는 <b>null 이냐 빈 배열이냐</b>다. 종전엔 셋 다 "연결 전부 삭제" 로 흘러,
     * 값을 빠뜨린 요청이 붙여 둔 태그를 조용히 다 지웠다(QA #87). 지우는 것은 되돌릴 수 없으므로
     * <b>말한 적 없는 것을 지움으로 읽지 않는다</b> — 빈 배열({@code []})만 "전부 떼 달라" 는
     * 뜻으로 인정한다.
     *
     * <p>웹({@code todoApi.updateTags})도 앱({@code todo_repository.updateTags})도 이미
     * {@code tagIds} 를 늘 실어 보내므로 이 제약에 걸리는 화면은 없다.
     *
     * <p>스키마 이름이 {@code ...Assign...} 인 것은 <b>이름 충돌 때문</b>이다 —
     * {@code TodoTagApiDto.UpdateRequest}(태그 이름·색 수정)가 이미
     * {@code TodoTagUpdateRequest} 를 쓰고 있어, 같은 이름을 주면 OpenAPI 문서에서 한쪽이
     * 조용히 덮인다.
     */
    @Schema(name = "TodoTagAssignRequest")
    public record TagUpdateRequest(
        @NotNull(message = "붙일 태그를 알려 주세요. 모두 떼려면 빈 목록을 보내 주세요")
        List<Long> tagIds
    ) {}

    /**
     * {@code PATCH /todo/{id}/status} 의 본문 — <b>없어도 된다</b>.
     *
     * <p>본문이 없으면 종전대로 완료 ↔ 대기 토글이고(웹이 그렇게 부른다), 값을 실으면 그 상태로
     * 간다. {@code status} 만 null 인 {@code {}} 도 토글로 읽는다 — 이쪽은 지우는 동작이 아니라
     * <b>정보가 없을 때의 기본값이 이미 정해져 있는</b> 자리라서, 태그({@link TagUpdateRequest})와
     * 답이 다르다.
     */
    @Schema(name = "TodoStatusUpdateRequest")
    public record StatusUpdateRequest(
        TodoStatus status
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
