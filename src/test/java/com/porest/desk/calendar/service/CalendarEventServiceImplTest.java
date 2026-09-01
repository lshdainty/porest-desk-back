package com.porest.desk.calendar.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.EventLabelRepository;
import com.porest.desk.calendar.repository.EventReminderRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 캘린더 이벤트 서비스 회귀 방지 단위 테스트 — 날짜 범위 검증 + 공유 캘린더 쓰기 권한 위임.
 */
@ExtendWith(MockitoExtension.class)
class CalendarEventServiceImplTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private EventLabelRepository eventLabelRepository;
    @Mock private EventReminderRepository eventReminderRepository;
    @Mock private UserCalendarRepository userCalendarRepository;
    @Mock private UserCalendarService userCalendarService;
    @Mock private UserRepository userRepository;
    @Mock private CalendarMembershipValidator calendarMembershipValidator;

    @InjectMocks private CalendarEventServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private CalendarEventServiceDto.CreateCommand createCmd(
            LocalDateTime start, LocalDateTime end, Long calendarRowId) {
        return new CalendarEventServiceDto.CreateCommand(
                USER_ID, "회의", null, null, null, start, end, null,
                null, null, null, null, calendarRowId);
    }

    @Test
    @DisplayName("getEvents — 반복(rrule) 이벤트는 구간 안 발생들로 전개된다")
    void getEventsExpandsRecurringIntoOccurrences() {
        given(calendarMembershipValidator.getAccessibleCalendarIds(USER_ID))
                .willReturn(java.util.List.of(9L));
        CalendarEvent weekly = CalendarEvent.createEvent(user(USER_ID), "주간회의", null,
                null, null,
                LocalDateTime.of(2026, 10, 3, 0, 0), LocalDateTime.of(2026, 10, 3, 23, 59),
                null, null, null, "FREQ=WEEKLY", mock(UserCalendar.class));
        ReflectionTestUtils.setField(weekly, "rowId", 162L);
        given(calendarEventRepository.findByCalendarIdsAndDateRange(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.List.of(weekly));
        given(eventReminderRepository.findByEventIds(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.List.of());

        var out = sut.getEvents(USER_ID,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59));

        // 10월: 3·10·17·24·31 — dev 에선 3일 한 번만 떴다
        org.assertj.core.api.Assertions.assertThat(out).hasSize(5);
        org.assertj.core.api.Assertions.assertThat(out)
                .extracting(e -> e.startDate().getDayOfMonth())
                .containsExactly(3, 10, 17, 24, 31);
        // 발생은 전부 원본 rowId 를 유지한다 — 수정·삭제가 시리즈에 걸리도록
        org.assertj.core.api.Assertions.assertThat(out)
                .allMatch(e -> e.rowId() == 162L);
    }

    @Test
    @DisplayName("createEvent — 시작이 종료보다 늦으면 거부")
    void createRejectsInvalidDateRange() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        var cmd = createCmd(LocalDateTime.of(2026, 6, 2, 10, 0), LocalDateTime.of(2026, 6, 1, 10, 0), null);

        assertThatThrownBy(() -> sut.createEvent(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("getEvents — 시작이 종료보다 늦으면 거부")
    void getEventsRejectsInvalidDateRange() {
        assertThatThrownBy(() -> sut.getEvents(USER_ID,
                LocalDateTime.of(2026, 6, 2, 0, 0), LocalDateTime.of(2026, 6, 1, 0, 0)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createEvent — 공유 캘린더에 쓰기 권한이 없으면 거부")
    void createRejectsWhenNoWritePermission() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(userCalendarRepository.findById(7L)).willReturn(Optional.of(mock(UserCalendar.class)));
        willThrow(new ForbiddenException(DeskErrorCode.CALENDAR_EVENT_ACCESS_DENIED))
                .given(calendarMembershipValidator).validateCanWrite(7L, USER_ID);

        var cmd = createCmd(LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0), 7L);

        assertThatThrownBy(() -> sut.createEvent(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createEvent — 남의 라벨은 이벤트에 부착 불가(소유권 검증 누락 보강)")
    void createRejectsOthersLabel() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        EventLabel othersLabel = mock(EventLabel.class);
        given(othersLabel.getUser()).willReturn(user(999L));
        given(eventLabelRepository.findById(30L)).willReturn(Optional.of(othersLabel));

        var cmd = new CalendarEventServiceDto.CreateCommand(
                USER_ID, "회의", null, null, null,
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0), null,
                30L, null, null, null, null);

        assertThatThrownBy(() -> sut.createEvent(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteEvent — 생성자(user)가 삭제된 stale 이벤트도 EDIT 권한이면 NPE 없이 삭제")
    void deleteStaleEventWithNullCreator() {
        CalendarEvent event = mock(CalendarEvent.class);
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(50L);
        given(event.getCalendar()).willReturn(cal);
        given(event.getUser()).willReturn(null); // 생성자 삭제됨(stale)
        given(calendarEventRepository.findById(5L)).willReturn(Optional.of(event));
        UserCalendarMember member = mock(UserCalendarMember.class);
        given(calendarMembershipValidator.validateMembership(50L, USER_ID)).willReturn(member);
        given(calendarMembershipValidator.canEditOrDelete(member, null, USER_ID)).willReturn(true);

        sut.deleteEvent(5L, USER_ID); // 가드 없으면 event.getUser().getRowId() 에서 NPE

        verify(event).deleteEvent();
    }
}
