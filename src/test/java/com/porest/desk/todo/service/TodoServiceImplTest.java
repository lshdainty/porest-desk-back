package com.porest.desk.todo.service;

import com.porest.desk.common.patch.Patch;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.domain.TodoTagMapping;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagMappingRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 할일 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 할일/프로젝트는 접근·수정·삭제할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceImplTest {

    @Mock private TodoRepository todoRepository;
    @Mock private TodoTagRepository todoTagRepository;
    @Mock private TodoTagMappingRepository todoTagMappingRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.porest.desk.constellation.service.StarlightService starlightService;
    @Mock private TodoTagService todoTagService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private TodoServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    @Test
    @DisplayName("getTodo — 남의 할일은 조회 불가")
    void getRejectsOthers() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(999L));
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));

        assertThatThrownBy(() -> sut.getTodo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateTodo — 남의 할일은 수정 불가")
    void updateRejectsOthers() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(999L));
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));

        assertThatThrownBy(() -> sut.updateTodo(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteTodo — 남의 할일은 삭제 불가")
    void deleteRejectsOthers() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(999L));
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));

        assertThatThrownBy(() -> sut.deleteTodo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── 정상 CRUD 결과 정확성 ─────────────────────────────
    @Test
    @DisplayName("createTodo — TASK 기본값(status=PENDING, sortOrder=0, isPinned=N)·필드 1:1 매핑")
    void createTodoTaskDefaults() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(todoRepository.save(any(Todo.class))).willAnswer(inv -> {
            Todo t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", 100L);
            return t;
        });
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var cmd = new TodoServiceDto.CreateCommand(
                USER_ID, "기획서 작성", "초안", TodoPriority.HIGH, "업무",
                LocalDate.of(2026, 6, 20), null, null, TodoType.TASK);
        var info = sut.createTodo(cmd);

        assertThat(info.type()).isEqualTo(TodoType.TASK);
        assertThat(info.title()).isEqualTo("기획서 작성");
        assertThat(info.content()).isEqualTo("초안");
        assertThat(info.priority()).isEqualTo(TodoPriority.HIGH);
        assertThat(info.category()).isEqualTo("업무");
        assertThat(info.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(info.sortOrder()).isEqualTo(0);
        assertThat(info.isPinned()).isEqualTo(YNType.N);
        assertThat(info.completedAt()).isNull();
        assertThat(info.subtaskCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("createTodo — NOTE 타입은 priority 가 LOW 로 강제된다")
    void createTodoNoteForcesLowPriority() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(todoRepository.save(any(Todo.class))).willAnswer(inv -> {
            Todo t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", 101L);
            return t;
        });
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var cmd = new TodoServiceDto.CreateCommand(
                USER_ID, "메모성 노트", null, TodoPriority.HIGH, null, null, null, null, TodoType.NOTE);
        var info = sut.createTodo(cmd);

        assertThat(info.type()).isEqualTo(TodoType.NOTE);
        assertThat(info.priority()).isEqualTo(TodoPriority.LOW); // 입력 HIGH 무시
    }

    /**
     * QA #81 — {@code todo.priority} 는 NOT NULL 인데 앱의 <b>하위 할 일 빠른 추가</b>가
     * 제목만 보낸다({@code todo_edit_dialog.dart} 의 {@code repo.create(title: title)}).
     * 그래서 그 화면은 지금 운영에서 저장이 안 되고, 받는 답은 "다른 곳에서 먼저 수정됐어요" 다.
     *
     * <p>답은 거절이 아니라 기본값이다 — 안 보낸 중요도는 "보통" 으로 읽는다. 그래서 DTO 에
     * {@code @NotNull} 을 걸지 않았다(걸면 그 화면이 400 으로 계속 막힌다).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TodoServiceImpl.createTodo} 의
     * {@code command.priority() != null ? ... : MEDIUM} 을 {@code command.priority()} 로
     * 되돌리면 아래가 null 을 만나 깨진다.
     */
    @Test
    @DisplayName("createTodo — priority 가 없으면 MEDIUM 으로 저장한다(앱의 하위 할 일 빠른 추가)")
    void createTodoDefaultsPriorityToMedium() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(todoRepository.save(any(Todo.class))).willAnswer(inv -> {
            Todo t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", 102L);
            return t;
        });
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var cmd = new TodoServiceDto.CreateCommand(
                USER_ID, "장보기", null, null, null, null, null, null, null);
        var info = sut.createTodo(cmd);

        assertThat(info.priority()).isEqualTo(TodoPriority.MEDIUM);
        assertThat(info.type()).isEqualTo(TodoType.TASK);
    }

    @Test
    @DisplayName("changeStatus(null) — 본문 없는 옛 요청: PENDING→COMPLETED, completedAt 세팅")
    void toggleStatusToCompleted() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 7L);
        given(todoRepository.findById(7L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.changeStatus(7L, USER_ID, null);

        assertThat(info.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(info.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus(null) — 본문 없는 옛 요청: COMPLETED→PENDING, completedAt 클리어")
    void toggleStatusBackToPending() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 8L);
        todo.toggleStatus(); // 먼저 COMPLETED 로
        given(todoRepository.findById(8L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.changeStatus(8L, USER_ID, null);

        assertThat(info.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(info.completedAt()).isNull();
    }

    /**
     * QA #93 — 종전엔 본문을 안 읽고 토글만 해서 "진행 중" 이 완료가 됐다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code changeStatus} 안에서 {@code todo.changeStatus(status)}
     * 대신 {@code todo.toggleStatus()} 를 부르면(= 종전 동작) 상태가 COMPLETED 가 되어 깨진다.
     */
    @Test
    @DisplayName("changeStatus(IN_PROGRESS) — 지정한 상태로 간다(완료로 튀지 않는다)")
    void changeStatusToInProgress() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 9L);
        given(todoRepository.findById(9L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.changeStatus(9L, USER_ID, TodoStatus.IN_PROGRESS);

        assertThat(info.status()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(info.completedAt()).isNull();
    }

    /** 완료였던 할 일을 진행 중으로 되돌리면 완료 시각도 지워지고 별빛은 회수 경로로 간다. */
    @Test
    @DisplayName("changeStatus(IN_PROGRESS) — 완료였다면 completedAt 을 지운다")
    void changeStatusFromCompletedToInProgressClearsCompletedAt() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 10L);
        todo.toggleStatus(); // 먼저 COMPLETED 로
        given(todoRepository.findById(10L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.changeStatus(10L, USER_ID, TodoStatus.IN_PROGRESS);

        assertThat(info.status()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(info.completedAt()).isNull();
    }

    /**
     * 같은 상태를 다시 보내면 완료 시각이 밀리면 안 된다 — 앱이 재시도하거나 두 번 눌렀을 때
     * "언제 끝냈는지" 가 요청할 때마다 달라진다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code Todo.changeStatus} 의
     * {@code next == this.status} 조기 반환을 빼면 completedAt 이 새로 찍혀 깨진다.
     */
    @Test
    @DisplayName("changeStatus(COMPLETED) — 이미 완료면 completedAt 을 다시 찍지 않는다")
    void changeStatusToSameStatusKeepsCompletedAt() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 11L);
        todo.toggleStatus(); // COMPLETED
        var completedAt = todo.getCompletedAt();
        given(todoRepository.findById(11L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.changeStatus(11L, USER_ID, TodoStatus.COMPLETED);

        assertThat(info.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(info.completedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("changeStatus — 남의 할일 상태는 바꿀 수 없다")
    void changeStatusRejectsOthers() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(999L));
        given(todoRepository.findById(12L)).willReturn(Optional.of(todo));

        assertThatThrownBy(() -> sut.changeStatus(12L, USER_ID, TodoStatus.COMPLETED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("reorderTodos — 남의 할일 순서는 변경 불가(소유권 검증 누락 보강)")
    void reorderRejectsOthers() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(999L));
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));

        var cmd = new TodoServiceDto.ReorderCommand(
                List.of(new TodoServiceDto.ReorderCommand.ReorderItem(5L, 1)));

        assertThatThrownBy(() -> sut.reorderTodos(USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * QA #81 — 수정에서 {@code priority} 가 빠지면 <b>기존 값을 지킨다</b>. 종전엔 null 을 그대로
     * 덮어써 NOT NULL 위반 → 409 "다른 곳에서 먼저 수정됐어요" 였다. 생성처럼 기본값(MEDIUM)을
     * 씌우지 않는 이유는 이미 사용자가 정한 값이 있기 때문이다 — 씌우면 HIGH 로 둔 할 일이
     * 제목만 고쳤는데 보통으로 내려앉는다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code Todo.updateTodo} 의 {@code if (priority != null)}
     * 가드를 빼면 아래가 null 을 만나 깨진다.
     */
    @Test
    @DisplayName("updateTodo — priority 가 없으면 기존 값을 지킨다(기본값으로 덮지 않는다)")
    void updateKeepsExistingPriorityWhenAbsent() {
        Todo todo = Todo.createTodo(user(USER_ID), "원제목", "내용", TodoPriority.HIGH, "업무",
                LocalDate.of(2026, 6, 20), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 5L);
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(5L)).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.updateTodo(5L, USER_ID, new TodoServiceDto.UpdateCommand(
                Patch.set("고친제목"), Patch.set("내용"), Patch.absent(), Patch.set("업무"), Patch.set(LocalDate.of(2026, 6, 20)), null));

        assertThat(info.priority()).isEqualTo(TodoPriority.HIGH);
        assertThat(info.title()).isEqualTo("고친제목");
    }

    // ── QA #79 — 서버가 category ↔ 태그 다리를 놓는다 ─────────────────────────
    // 웹·앱 어느 쪽도 tagIds 를 보내지 않는다(보내는 것은 category 문자열 하나다).
    // 그래서 매핑 테이블이 비고, 매핑으로 세는 사용 수도 0 이 된다.

    private TodoTag tag(long rowId, String name, long ownerRowId) {
        TodoTag t = TodoTag.createTag(user(ownerRowId), name, "#fff");
        ReflectionTestUtils.setField(t, "rowId", rowId);
        return t;
    }

    private void stubTodoSave(long rowId) {
        given(todoRepository.save(any(Todo.class))).willAnswer(inv -> {
            Todo t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", rowId);
            return t;
        });
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());
    }

    private TodoTag savedMappingTag() {
        ArgumentCaptor<TodoTagMapping> captor = ArgumentCaptor.forClass(TodoTagMapping.class);
        verify(todoTagMappingRepository).save(captor.capture());
        return captor.getValue().getTag();
    }

    /**
     * QA #79 — 클라이언트를 고치지 않고도 매핑이 차게 만드는 자리.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TodoServiceImpl.resolveTagsForWrite} 에서
     * {@code resolveCategoryTag(...)} 줄을 지우고 {@code List.of()} 를 돌려주면 매핑이 하나도
     * 안 남아 아래 {@code verify(...).save(...)} 가 깨진다.
     */
    @Test
    @DisplayName("createTodo — tagIds 가 없으면 category 로 태그를 확보해 매핑을 남긴다")
    void createBridgesCategoryToTag() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        stubTodoSave(200L);
        given(todoTagService.findOrCreateByName(USER_ID, "업무")).willReturn(42L);
        given(todoTagRepository.findById(42L)).willReturn(Optional.of(tag(42L, "업무", USER_ID)));

        sut.createTodo(new TodoServiceDto.CreateCommand(
                USER_ID, "기획서", null, TodoPriority.MEDIUM, "업무", null, null, null, TodoType.TASK));

        assertThat(savedMappingTag().getRowId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("createTodo — category 가 비었으면 태그를 만들지 않는다")
    void createDoesNotCreateTagForBlankCategory() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        stubTodoSave(201L);

        sut.createTodo(new TodoServiceDto.CreateCommand(
                USER_ID, "장보기", null, null, "   ", null, null, null, null));

        verify(todoTagService, never()).findOrCreateByName(anyLong(), anyString());
        verify(todoTagMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTodo — tagIds 를 명시하면 category 다리보다 그쪽이 이긴다")
    void explicitTagIdsWinOverCategory() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        stubTodoSave(202L);
        given(todoTagRepository.findAllByIds(List.of(7L))).willReturn(List.of(tag(7L, "리뷰", USER_ID)));

        sut.createTodo(new TodoServiceDto.CreateCommand(
                USER_ID, "코드 리뷰", null, null, "업무", null, null, List.of(7L), null));

        verify(todoTagService, never()).findOrCreateByName(anyLong(), anyString());
        assertThat(savedMappingTag().getRowId()).isEqualTo(7L);
    }

    /**
     * QA #79 — <b>응답 유출</b>. {@code findAllByIds} 에 소유권 검사가 없어 남의 {@code tagId} 를
     * 실으면 그 태그의 이름·색이 내 할 일 응답 {@code tags[]} 로 나갔다. 캘린더 라벨과 같은
     * 모양으로 막는다({@code CalendarEventServiceImpl.validateLabelOwnership}).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code resolveOwnedTags} 의
     * {@code validateTagOwnership(tag, userRowId)} 루프를 지우면 예외 없이 남의 태그가 매핑된다.
     */
    @Test
    @DisplayName("createTodo — 남의 태그를 지목하면 403 이고 매핑도 남지 않는다")
    void rejectsForeignTagIds() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(todoTagRepository.findAllByIds(List.of(7L))).willReturn(List.of(tag(7L, "남의태그", 999L)));

        assertThatThrownBy(() -> sut.createTodo(new TodoServiceDto.CreateCommand(
                USER_ID, "훔쳐보기", null, null, null, null, null, List.of(7L), null)))
                .isInstanceOf(ForbiddenException.class);
        verify(todoTagMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTags — 없는(또는 지워진) 태그를 지목하면 404 로 답한다")
    void rejectsUnknownTagIds() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", null, TodoPriority.MEDIUM, null, null, null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 5L);
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        given(todoTagRepository.findAllByIds(List.of(7L))).willReturn(List.of());

        assertThatThrownBy(() -> sut.updateTags(5L, USER_ID, List.of(7L)))
                .isInstanceOf(EntityNotFoundException.class);
        verify(todoTagMappingRepository, never()).save(any());
    }

    /**
     * QA #79 — category 를 바꾸면 <b>옛 이름의 태그 매핑을 걷는다</b>. 안 걷으면 사용 수가
     * 옛 태그에 계속 잡혀 "지금 아무도 안 쓰는 태그" 가 사용 중으로 보인다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TodoServiceImpl.updateTodo} 의
     * {@code String previousCategory = todo.getCategory();} 를 {@code todo.updateTodo(...)}
     * 아래로 옮기면 옛 이름이 이미 "개인" 이라 아래 {@code deleteByTodoIdAndTagId(5,11)} 이
     * 불리지 않아 깨진다.
     */
    @Test
    @DisplayName("updateTodo — category 가 바뀌면 옛 태그 매핑을 걷고 새 태그를 붙인다")
    void updateMovesMappingWhenCategoryChanges() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", null, TodoPriority.MEDIUM, "업무",
                null, null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 5L);
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        given(todoTagService.findOrCreateByName(USER_ID, "개인")).willReturn(12L);
        given(todoTagRepository.findById(12L)).willReturn(Optional.of(tag(12L, "개인", USER_ID)));
        given(todoTagRepository.findActiveByUserAndName(USER_ID, "업무"))
                .willReturn(Optional.of(tag(11L, "업무", USER_ID)));
        given(todoTagMappingRepository.findByTodoId(5L)).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        sut.updateTodo(5L, USER_ID, new TodoServiceDto.UpdateCommand(
                Patch.set("t"), Patch.absent(), Patch.absent(), Patch.set("개인"), Patch.absent(), null));

        verify(todoTagMappingRepository).deleteByTodoIdAndTagId(5L, 11L);
        assertThat(savedMappingTag().getRowId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("updateTodo — category 가 그대로고 이미 매핑돼 있으면 아무것도 만들지 않는다")
    void updateKeepsExistingMappingWhenCategoryUnchanged() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", null, TodoPriority.MEDIUM, "업무",
                null, null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 5L);
        TodoTag work = tag(11L, "업무", USER_ID);
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        given(todoTagService.findOrCreateByName(USER_ID, "업무")).willReturn(11L);
        given(todoTagRepository.findById(11L)).willReturn(Optional.of(work));
        given(todoTagMappingRepository.findByTodoId(5L))
                .willReturn(List.of(TodoTagMapping.create(todo, work)));
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        sut.updateTodo(5L, USER_ID, new TodoServiceDto.UpdateCommand(
                Patch.set("t"), Patch.absent(), Patch.absent(), Patch.set("업무"), Patch.absent(), null));

        verify(todoTagMappingRepository, never()).deleteByTodoIdAndTagId(anyLong(), anyLong());
        verify(todoTagMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTodo — tagIds 를 명시하면 매핑을 통째로 갈아 끼운다(다리는 타지 않는다)")
    void updateWithExplicitTagIdsReplacesMappings() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", null, TodoPriority.MEDIUM, "업무",
                null, null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 5L);
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        given(todoTagRepository.findAllByIds(List.of(7L))).willReturn(List.of(tag(7L, "리뷰", USER_ID)));
        given(todoTagMappingRepository.findByTodoId(5L)).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        sut.updateTodo(5L, USER_ID, new TodoServiceDto.UpdateCommand(
                Patch.set("t"), Patch.absent(), Patch.absent(), Patch.set("업무"), Patch.absent(), List.of(7L)));

        verify(todoTagService, never()).findOrCreateByName(anyLong(), anyString());
        verify(todoTagMappingRepository).deleteByTodoId(5L);
        assertThat(savedMappingTag().getRowId()).isEqualTo(7L);
    }
}
