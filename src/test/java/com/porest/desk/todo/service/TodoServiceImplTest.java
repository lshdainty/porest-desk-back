package com.porest.desk.todo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoProject;
import com.porest.desk.todo.repository.TodoProjectRepository;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagMappingRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 할일 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 할일/프로젝트는 접근·수정·삭제할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceImplTest {

    @Mock private TodoRepository todoRepository;
    @Mock private TodoProjectRepository todoProjectRepository;
    @Mock private TodoTagRepository todoTagRepository;
    @Mock private TodoTagMappingRepository todoTagMappingRepository;
    @Mock private UserRepository userRepository;

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

    @Test
    @DisplayName("createTodo — 남의 프로젝트에는 할일 생성 불가")
    void createRejectsOthersProject() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        TodoProject project = mock(TodoProject.class);
        given(project.getUser()).willReturn(user(999L));
        given(todoProjectRepository.findById(20L)).willReturn(Optional.of(project));

        var cmd = new TodoServiceDto.CreateCommand(
                USER_ID, "할일", null, null, null, null, 20L, null, null, null);

        assertThatThrownBy(() -> sut.createTodo(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateTodo — 남의 프로젝트로 이동 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersProject() {
        Todo todo = mock(Todo.class);
        given(todo.getUser()).willReturn(user(USER_ID));
        given(todoRepository.findById(5L)).willReturn(Optional.of(todo));
        TodoProject othersProject = mock(TodoProject.class);
        given(othersProject.getUser()).willReturn(user(999L));
        given(todoProjectRepository.findById(20L)).willReturn(Optional.of(othersProject));

        var cmd = new TodoServiceDto.UpdateCommand(
                "수정", null, null, null, null, 20L, null);

        assertThatThrownBy(() -> sut.updateTodo(5L, USER_ID, cmd))
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
}
