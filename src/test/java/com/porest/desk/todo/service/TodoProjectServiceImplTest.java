package com.porest.desk.todo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.todo.domain.TodoProject;
import com.porest.desk.todo.repository.TodoProjectRepository;
import com.porest.desk.todo.service.dto.TodoProjectServiceDto;
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
 * 할일 프로젝트 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class TodoProjectServiceImplTest {

    @Mock private TodoProjectRepository todoProjectRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TodoProjectServiceImpl sut;

    private static final long USER_ID = 1L;

    private TodoProject othersProject() {
        User u = User.createUser(null, "x", "남", "x@porest.com");
        ReflectionTestUtils.setField(u, "rowId", 999L);
        TodoProject p = mock(TodoProject.class);
        given(p.getUser()).willReturn(u);
        return p;
    }

    @Test
    @DisplayName("updateProject — 남의 프로젝트는 수정 불가")
    void updateRejectsOthers() {
        TodoProject p = othersProject();
        given(todoProjectRepository.findById(5L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.updateProject(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteProject — 남의 프로젝트는 삭제 불가")
    void deleteRejectsOthers() {
        TodoProject p = othersProject();
        given(todoProjectRepository.findById(5L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.deleteProject(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("reorderProjects — 남의 프로젝트 순서는 변경 불가(소유권 검증 누락 보강)")
    void reorderRejectsOthers() {
        TodoProject p = othersProject();
        given(todoProjectRepository.findById(5L)).willReturn(Optional.of(p));

        var cmd = new TodoProjectServiceDto.ReorderCommand(
                List.of(new TodoProjectServiceDto.ReorderCommand.ReorderItem(5L, 1)));

        assertThatThrownBy(() -> sut.reorderProjects(USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }
}
