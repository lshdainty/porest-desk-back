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
import com.porest.desk.common.patch.Patch;
import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
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

    /**
     * QA #81 — 신고된 그 자리. {@code calendar_event.event_type} 은 NOT NULL 인데 화면이 값을
     * 정해 주지 않으면 그대로 null 이 내려가 저장이 터졌고, 공통 핸들러가 그걸
     * <b>409 "다른 곳에서 먼저 수정됐어요"</b> 로 포장했다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code CalendarEvent.createEvent} 의
     * {@code eventType != null ? eventType : PERSONAL} 을 {@code eventType} 으로 되돌리면
     * 아래 {@code isEqualTo(PERSONAL)} 이 null 을 만나 깨진다.
     */
    @Test
    @DisplayName("createEvent — eventType 이 없으면 개인 일정(PERSONAL)으로 저장한다")
    void createDefaultsEventTypeToPersonal() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(userCalendarService.getOrCreateDefault(USER_ID))
                .willReturn(mock(UserCalendarServiceDto.CalendarInfo.class));
        given(userCalendarRepository.findById(org.mockito.ArgumentMatchers.any()))
                .willReturn(Optional.of(mock(UserCalendar.class)));

        var out = sut.createEvent(createCmd(
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0), null));

        org.assertj.core.api.Assertions.assertThat(out.eventType())
                .isEqualTo(com.porest.desk.calendar.type.CalendarEventType.PERSONAL);
    }

    /**
     * 시작·종료가 아예 없으면 종전엔 {@code command.startDate().isAfter(...)} 에서 NPE 가 나
     * <b>500</b> 이 나갔다(QA #81). 되돌려 보는 법: {@code validateDateRange} 의 널 검사를 빼면
     * 아래가 {@code InvalidValueException} 대신 NPE 로 깨진다.
     */
    @Test
    @DisplayName("createEvent — 시작 일시가 없으면 400 이다(500 이 아니다)")
    void createRejectsNullDatesWithoutNpe() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        assertThatThrownBy(() -> sut.createEvent(createCmd(null, LocalDateTime.of(2026, 6, 1, 11, 0), null)))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
    }

    /**
     * 수정에서 {@code eventType} 이 빠지면 <b>기존 값을 지킨다</b> — 기본값을 씌우면 업무 일정이
     * 조용히 개인 일정으로 바뀐다. 되돌려 보는 법: {@code CalendarEvent.updateEvent} 의
     * {@code if (eventType != null)} 가드를 빼면 아래가 null 을 만나 깨진다.
     */
    @Test
    @DisplayName("updateEvent — eventType 이 없으면 기존 값을 지킨다(기본값으로 덮지 않는다)")
    void updateKeepsExistingEventTypeWhenAbsent() {
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(50L);
        CalendarEvent event = CalendarEvent.createEvent(user(USER_ID), "회의", null,
                com.porest.desk.calendar.type.CalendarEventType.WORK, null,
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0),
                null, null, null, null, cal);
        ReflectionTestUtils.setField(event, "rowId", 5L);
        given(calendarEventRepository.findById(5L)).willReturn(Optional.of(event));
        UserCalendarMember member = mock(UserCalendarMember.class);
        given(calendarMembershipValidator.validateMembership(50L, USER_ID)).willReturn(member);
        given(calendarMembershipValidator.canEditOrDelete(member, USER_ID, USER_ID)).willReturn(true);
        given(eventReminderRepository.findByEventId(5L)).willReturn(java.util.List.of());

        sut.updateEvent(5L, USER_ID, new CalendarEventServiceDto.UpdateCommand(
                "회의(수정)", Patch.absent(), null, Patch.absent(),
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0),
                null, Patch.absent(), Patch.absent(), Patch.absent(), null, Patch.absent()));

        org.assertj.core.api.Assertions.assertThat(event.getEventType())
                .isEqualTo(com.porest.desk.calendar.type.CalendarEventType.WORK);
        org.assertj.core.api.Assertions.assertThat(event.getIsAllDay()).isNotNull();
    }

    // ── D4 일정은 반드시 캘린더에 속한다 (사용자 결정 2026-09-08) ──────────────

    /** 소속 캘린더 말고는 아무것도 안 건드리는 수정 명령. */
    private CalendarEventServiceDto.UpdateCommand calendarOnlyCmd(Patch<Long> calendarRowId) {
        return new CalendarEventServiceDto.UpdateCommand(
                null, Patch.absent(), null, Patch.absent(),
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0),
                null, Patch.absent(), Patch.absent(), Patch.absent(), null, calendarRowId);
    }

    /** 캘린더에 붙어 있는 일정 하나 — 수정 권한까지 열어 둔다. */
    private CalendarEvent eventInCalendar(UserCalendar calendar, long calendarRowId) {
        CalendarEvent event = CalendarEvent.createEvent(user(USER_ID), "회의", null, null, null,
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0),
                null, null, null, null, calendar);
        ReflectionTestUtils.setField(event, "rowId", 5L);
        given(calendarEventRepository.findById(5L)).willReturn(Optional.of(event));
        UserCalendarMember member = mock(UserCalendarMember.class);
        given(calendarMembershipValidator.validateMembership(calendarRowId, USER_ID)).willReturn(member);
        given(calendarMembershipValidator.canEditOrDelete(member, USER_ID, USER_ID)).willReturn(true);
        return event;
    }

    /**
     * 명시적 {@code null} 은 "소속을 뗀다" 는 뜻인데, 일정은 캘린더 없이 존재할 수 없다.
     * 기본 캘린더로 대신 채우지 <b>않는</b> 이유는 공유 캘린더에 있던 일정이 아무도 시키지
     * 않은 채 내 개인 캘린더로 빠져나가기 때문이다 — 같이 보던 사람 화면에서 조용히 사라진다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code applyCalendar} 의 첫 {@code if} 를 지우면
     * 예외 없이 지금 캘린더를 그대로 둔 채 200 이 나가며 아래가 깨진다.
     */
    @Test
    @DisplayName("updateEvent — calendarRowId 에 명시적 null 을 실으면 400(소속은 뗄 수 없다)")
    void updateRejectsExplicitNullCalendar() {
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(50L);
        eventInCalendar(cal, 50L);

        assertThatThrownBy(() -> sut.updateEvent(5L, USER_ID, calendarOnlyCmd(Patch.set(null))))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.CALENDAR_EVENT_CALENDAR_REQUIRED);
    }

    /**
     * 키가 없는 것은 "소속을 건드리지 마라" 다. 여기서 거절하면 제목 한 줄 고치는 데도
     * 캘린더를 같이 실어야 하고, 웹·앱은 지금 그러지 않는다.
     */
    @Test
    @DisplayName("updateEvent — calendarRowId 키가 없으면 지금 캘린더를 그대로 둔다")
    void updateKeepsCalendarWhenKeyAbsent() {
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(50L);
        CalendarEvent event = eventInCalendar(cal, 50L);
        given(eventReminderRepository.findByEventId(5L)).willReturn(java.util.List.of());

        sut.updateEvent(5L, USER_ID, calendarOnlyCmd(Patch.absent()));

        org.assertj.core.api.Assertions.assertThat(event.getCalendar()).isSameAs(cal);
        verify(userCalendarService, org.mockito.Mockito.never()).getOrCreateDefault(USER_ID);
    }

    /**
     * <b>캘린더 없이 저장된 옛 일정</b>은 키가 없는 경로에서 기본 캘린더로 붙인다. 그냥 두면
     * 그 일정은 영영 소속이 없고(목록 조회는 접근 가능한 캘린더로만 긁으므로 화면에도 안 뜬다),
     * 거절하면 고칠 방법이 없는 채로 갇힌다. 붙이는 쪽만 빠져나갈 구멍이 있다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code applyCalendar} 의 마지막
     * {@code if (event.getCalendar() == null)} 갈래를 지우면 소속이 계속 null 로 남아 깨진다.
     */
    @Test
    @DisplayName("updateEvent — 캘린더 없이 저장된 옛 일정은 수정할 때 기본 캘린더로 붙는다")
    void updateAttachesDefaultCalendarToLegacyEventWithoutOne() {
        CalendarEvent legacy = CalendarEvent.createEvent(user(USER_ID), "옛 일정", null, null, null,
                LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0),
                null, null, null, null, null);
        ReflectionTestUtils.setField(legacy, "rowId", 5L);
        given(calendarEventRepository.findById(5L)).willReturn(Optional.of(legacy));
        given(eventReminderRepository.findByEventId(5L)).willReturn(java.util.List.of());
        UserCalendar defaultCal = mock(UserCalendar.class);
        given(userCalendarService.getOrCreateDefault(USER_ID))
                .willReturn(defaultCalendarInfo(70L));
        given(userCalendarRepository.findById(70L)).willReturn(Optional.of(defaultCal));

        sut.updateEvent(5L, USER_ID, calendarOnlyCmd(Patch.absent()));

        org.assertj.core.api.Assertions.assertThat(legacy.getCalendar()).isSameAs(defaultCal);
    }

    /** 옮기는 요청은 종전대로 — 옮겨 갈 캘린더의 쓰기 권한을 확인하고 붙인다. */
    @Test
    @DisplayName("updateEvent — calendarRowId 를 실으면 그 캘린더로 옮기고 쓰기 권한을 확인한다")
    void updateMovesCalendarWhenValuePresent() {
        UserCalendar cal = mock(UserCalendar.class);
        given(cal.getRowId()).willReturn(50L);
        CalendarEvent event = eventInCalendar(cal, 50L);
        UserCalendar target = mock(UserCalendar.class);
        given(eventReminderRepository.findByEventId(5L)).willReturn(java.util.List.of());
        given(userCalendarRepository.findById(60L)).willReturn(Optional.of(target));

        sut.updateEvent(5L, USER_ID, calendarOnlyCmd(Patch.set(60L)));

        org.assertj.core.api.Assertions.assertThat(event.getCalendar()).isSameAs(target);
        verify(calendarMembershipValidator).validateCanWrite(60L, USER_ID);
    }

    /**
     * 생성에서는 거절이 아니라 기본 캘린더를 대입한다 — 캘린더를 고르는 것은 사용자가 내린
     * 결정이 아니라 화면이 채워 주는 값이라, 안 왔을 때 400 을 던지면 사용자가 고칠 수 없는
     * 입력 때문에 일정 자체를 못 만든다.
     */
    @Test
    @DisplayName("createEvent — calendarRowId 가 없으면 기본 캘린더가 대입된다(거절하지 않는다)")
    void createFallsBackToDefaultCalendar() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        UserCalendar defaultCal = mock(UserCalendar.class);
        given(userCalendarService.getOrCreateDefault(USER_ID))
                .willReturn(defaultCalendarInfo(70L));
        given(userCalendarRepository.findById(70L)).willReturn(Optional.of(defaultCal));
        given(calendarEventRepository.save(org.mockito.ArgumentMatchers.any(CalendarEvent.class)))
                .willAnswer(inv -> {
                    CalendarEvent saved = inv.getArgument(0);
                    ReflectionTestUtils.setField(saved, "rowId", 9L);
                    return saved;
                });

        sut.createEvent(createCmd(LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), null));

        org.mockito.ArgumentCaptor<CalendarEvent> captor =
                org.mockito.ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCalendar()).isSameAs(defaultCal);
    }

    private UserCalendarServiceDto.CalendarInfo defaultCalendarInfo(long rowId) {
        return new UserCalendarServiceDto.CalendarInfo(rowId, USER_ID, "테스터", "기본", "#fff",
                0, true, true, null, false, true, null, 1, null, null);
    }
}
