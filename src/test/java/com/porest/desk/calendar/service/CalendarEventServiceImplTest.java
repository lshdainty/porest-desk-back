package com.porest.desk.calendar.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.UserCalendar;
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
}
