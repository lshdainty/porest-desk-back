package com.porest.desk.todo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import com.porest.desk.common.exception.DeskErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

/**
 * 할일 태그 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class TodoTagServiceImplTest {

    @Mock private TodoTagRepository todoTagRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TodoTagServiceImpl sut;

    private static final long USER_ID = 1L;

    private TodoTag othersTag() {
        User u = User.createUser(null, "x", "남", "x@porest.com");
        ReflectionTestUtils.setField(u, "rowId", 999L);
        TodoTag t = mock(TodoTag.class);
        given(t.getUser()).willReturn(u);
        return t;
    }

    @Test
    @DisplayName("updateTag — 남의 태그는 수정 불가")
    void updateRejectsOthers() {
        TodoTag t = othersTag();
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(t));

        assertThatThrownBy(() -> sut.updateTag(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteTag — 남의 태그는 삭제 불가")
    void deleteRejectsOthers() {
        TodoTag t = othersTag();
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(t));

        assertThatThrownBy(() -> sut.deleteTag(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTag — 활성 태그 중 같은 이름이 있으면 거부(중복 방지)")
    void createRejectsDuplicateActiveName() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(true);

        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "일상", "#fff")))
                .isInstanceOf(InvalidValueException.class);
    }
    @Test
    @DisplayName("createTag — 이름 앞뒤 공백은 저장 전에 잘린다(선행공백이 DB 판정과 어긋나던 자리)")
    void createTrimsName() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        var info = sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "  일상 ", "#fff"));

        assertThat(info.tagName()).isEqualTo("일상");
        verify(todoTagRepository).existsActiveByUserAndName(USER_ID, "일상", null);
    }

    @Test
    @DisplayName("createTag — 빈 이름·공백뿐인 이름은 400 으로 거절한다")
    void createRejectsBlankName() {
        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, " ", "#fff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("createTag — 유니크 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
    void translatesConstraintViolation() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(false);
        willThrow(new DataIntegrityViolationException("UK_todo_tag_user_active_name"))
                .given(todoTagRepository).flush();

        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "일상", "#fff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.TODO_TAG_DUPLICATE_NAME);
    }
}
