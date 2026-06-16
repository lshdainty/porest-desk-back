package com.porest.desk.calendar.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.calendar.domain.EventComment;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.EventCommentRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 일정 댓글 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class EventCommentServiceImplTest {

    @Mock private EventCommentRepository eventCommentRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private EventCommentServiceImpl sut;

    private static final long USER_ID = 1L;

    @Test
    @DisplayName("deleteComment — 남의 댓글은 삭제 불가")
    void deleteRejectsOthers() {
        User u = User.createUser(null, "x", "남", "x@porest.com");
        ReflectionTestUtils.setField(u, "rowId", 999L);
        EventComment comment = mock(EventComment.class);
        given(comment.getUser()).willReturn(u);
        given(eventCommentRepository.findById(5L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> sut.deleteComment(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
