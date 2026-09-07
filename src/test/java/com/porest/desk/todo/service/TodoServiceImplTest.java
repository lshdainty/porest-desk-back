package com.porest.desk.todo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.Todo;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
    @DisplayName("toggleStatus — PENDING→COMPLETED, completedAt 세팅")
    void toggleStatusToCompleted() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 7L);
        given(todoRepository.findById(7L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.toggleStatus(7L, USER_ID);

        assertThat(info.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(info.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("toggleStatus — COMPLETED→PENDING, completedAt 클리어")
    void toggleStatusBackToPending() {
        Todo todo = Todo.createTodo(user(USER_ID), "t", "c", TodoPriority.MEDIUM, "cat",
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", 8L);
        todo.toggleStatus(); // 먼저 COMPLETED 로
        given(todoRepository.findById(8L)).willReturn(Optional.of(todo));
        given(todoTagMappingRepository.findByTodoId(any())).willReturn(List.of());
        given(todoRepository.findSubtaskCountsByParentIds(any())).willReturn(Map.of());

        var info = sut.toggleStatus(8L, USER_ID);

        assertThat(info.status()).isEqualTo(TodoStatus.PENDING);
        assertThat(info.completedAt()).isNull();
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
                "고친제목", "내용", null, "업무", LocalDate.of(2026, 6, 20), null));

        assertThat(info.priority()).isEqualTo(TodoPriority.HIGH);
        assertThat(info.title()).isEqualTo("고친제목");
    }
}
