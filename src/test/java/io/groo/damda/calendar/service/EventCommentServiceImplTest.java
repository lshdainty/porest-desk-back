package io.groo.damda.calendar.service;

import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.InvalidValueException;
import io.groo.damda.calendar.domain.CalendarEvent;
import io.groo.damda.calendar.domain.EventComment;
import io.groo.damda.calendar.domain.UserCalendar;
import io.groo.damda.calendar.repository.CalendarEventRepository;
import io.groo.damda.calendar.repository.EventCommentRepository;
import io.groo.damda.calendar.service.dto.EventCommentServiceDto;
import io.groo.damda.common.exception.DeskErrorCode;
import io.groo.damda.user.domain.User;
import io.groo.damda.user.repository.UserRepository;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

/**
 * 일정 댓글 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class EventCommentServiceImplTest {

    @Mock private EventCommentRepository eventCommentRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private CalendarMembershipValidator calendarMembershipValidator;

    @InjectMocks private EventCommentServiceImpl sut;

    private static final long USER_ID = 1L;

    private CalendarEvent eventOnCalendar(long calendarRowId) {
        CalendarEvent event = mock(CalendarEvent.class);
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(calendarRowId);
        given(event.getCalendar()).willReturn(cal);
        return event;
    }

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

    @Test
    @DisplayName("createComment — 접근 권한 없는 이벤트엔 댓글 작성 불가(접근 검증 누락 보강)")
    void createRejectsWhenNoCalendarAccess() {
        CalendarEvent event = eventOnCalendar(50L);
        given(calendarEventRepository.findById(10L)).willReturn(Optional.of(event));
        willThrow(new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED))
                .given(calendarMembershipValidator).validateMembership(50L, USER_ID);

        var cmd = new EventCommentServiceDto.CreateCommand(10L, USER_ID, null, "댓글");

        assertThatThrownBy(() -> sut.createComment(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getComments — 접근 권한 없는 이벤트의 댓글은 조회 불가(읽기 접근 검증 누락 보강)")
    void getCommentsRejectsWhenNoCalendarAccess() {
        CalendarEvent event = eventOnCalendar(50L);
        given(calendarEventRepository.findById(10L)).willReturn(Optional.of(event));
        willThrow(new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED))
                .given(calendarMembershipValidator).validateMembership(50L, USER_ID);

        assertThatThrownBy(() -> sut.getComments(10L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createComment — 부모 댓글이 다른 이벤트 소속이면 거부(스레드 무결성 보강)")
    void createRejectsParentFromOtherEvent() {
        CalendarEvent event = eventOnCalendar(50L);
        given(event.getRowId()).willReturn(10L);
        given(calendarEventRepository.findById(10L)).willReturn(Optional.of(event));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        EventComment parent = mock(EventComment.class);
        CalendarEvent otherEvent = mock(CalendarEvent.class);
        given(otherEvent.getRowId()).willReturn(99L); // 다른 이벤트
        given(parent.getEvent()).willReturn(otherEvent);
        given(eventCommentRepository.findById(7L)).willReturn(Optional.of(parent));

        var cmd = new EventCommentServiceDto.CreateCommand(10L, USER_ID, 7L, "대댓글");

        assertThatThrownBy(() -> sut.createComment(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }
}
