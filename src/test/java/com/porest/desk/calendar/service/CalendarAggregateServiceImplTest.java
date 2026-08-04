package com.porest.desk.calendar.service;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.EventReminderRepository;
import com.porest.desk.calendar.service.dto.CalendarAggregateDto;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.service.dto.EventReminderServiceDto;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 캘린더 통합(집계) 서비스 회귀 방지 단위 테스트 — 이벤트/할일/거래를 본인 범위로 모으고
 * 리마인더를 이벤트별로 그룹핑하는 로직, 날짜→일시 범위 변환, 빈 결과 시 리마인더 조회 생략을 검증.
 * 실제 도메인 엔티티로 서비스의 조립 로직만 검증(레포는 mock).
 */
@ExtendWith(MockitoExtension.class)
class CalendarAggregateServiceImplTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private EventReminderRepository eventReminderRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private ExpenseRepository expenseRepository;

    @InjectMocks private CalendarAggregateServiceImpl sut;

    private static final long USER_ID = 1L;

    private final User owner = user(USER_ID);

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private CalendarEvent event(long rowId) {
        CalendarEvent e = CalendarEvent.createEvent(owner, "일정" + rowId, null,
                CalendarEventType.PERSONAL, null,
                LocalDateTime.of(2026, 6, 10, 9, 0), LocalDateTime.of(2026, 6, 10, 10, 0),
                YNType.N, null, null, null, null);
        ReflectionTestUtils.setField(e, "rowId", rowId);
        return e;
    }

    private EventReminder reminder(long rowId, CalendarEvent event, int minutesBefore) {
        EventReminder r = EventReminder.create(event, "NOTIFICATION", minutesBefore);
        ReflectionTestUtils.setField(r, "rowId", rowId);
        return r;
    }

    private Todo todo(long rowId) {
        Todo t = Todo.createTodo(owner, "할일" + rowId, null, TodoPriority.MEDIUM, null,
                LocalDate.of(2026, 6, 10), null, TodoType.TASK);
        ReflectionTestUtils.setField(t, "rowId", rowId);
        return t;
    }

    private Expense expense(long rowId, long amount) {
        Expense x = Expense.createExpense(owner, null, null, ExpenseType.EXPENSE, amount, null,
                LocalDateTime.of(2026, 6, 10, 12, 0), null, "CARD", null, null);
        ReflectionTestUtils.setField(x, "rowId", rowId);
        return x;
    }

    @Test
    @DisplayName("getAggregateData — 이벤트/할일/거래를 모으고 리마인더를 이벤트별로 그룹핑한다")
    void aggregatesAllSourcesAndGroupsRemindersPerEvent() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        CalendarEvent e1 = event(101L);
        CalendarEvent e2 = event(102L);
        CalendarEvent e3 = event(103L);   // 리마인더 없는 이벤트
        given(calendarEventRepository.findByUserAndDateRange(any(), any(), any()))
                .willReturn(List.of(e1, e2, e3));
        // 저장소는 이벤트 소속과 무관한 순서로 리마인더를 반환할 수 있다
        given(eventReminderRepository.findByEventIds(any()))
                .willReturn(List.of(
                        reminder(201L, e1, 10),
                        reminder(203L, e2, 60),
                        reminder(202L, e1, 30)));
        given(todoRepository.findByUserAndDueDateBetween(any(), any(), any()))
                .willReturn(List.of(todo(301L), todo(302L)));
        given(expenseRepository.findByUser(any(), any(), any(), any(), any()))
                .willReturn(List.of(expense(401L, 5_000L), expense(402L, 3_000L)));

        CalendarAggregateDto.AggregateData result = sut.getAggregateData(USER_ID, start, end);

        // 이벤트: 저장소 순서 보존, 전건 매핑
        assertThat(result.events()).extracting(CalendarEventServiceDto.EventInfo::rowId)
                .containsExactly(101L, 102L, 103L);

        // 리마인더는 각 이벤트에 소속(event_row_id)대로 그룹핑되어 부착된다
        var e1Reminders = result.events().get(0).reminders();
        assertThat(e1Reminders).extracting(EventReminderServiceDto.ReminderInfo::rowId)
                .containsExactlyInAnyOrder(201L, 202L);
        assertThat(e1Reminders).allSatisfy(r -> assertThat(r.eventRowId()).isEqualTo(101L));

        var e2Reminders = result.events().get(1).reminders();
        assertThat(e2Reminders).extracting(EventReminderServiceDto.ReminderInfo::rowId)
                .containsExactly(203L);
        assertThat(e2Reminders).allSatisfy(r -> assertThat(r.eventRowId()).isEqualTo(102L));

        assertThat(result.events().get(2).reminders()).isEmpty();

        // 할일/거래: 전건 매핑 + 본인(userRowId) 데이터
        assertThat(result.todos()).extracting(TodoServiceDto.TodoInfo::rowId)
                .containsExactly(301L, 302L);
        assertThat(result.todos()).allSatisfy(t -> assertThat(t.userRowId()).isEqualTo(USER_ID));

        assertThat(result.expenses()).extracting(ExpenseServiceDto.ExpenseInfo::rowId)
                .containsExactly(401L, 402L);
        assertThat(result.expenses()).allSatisfy(x -> assertThat(x.userRowId()).isEqualTo(USER_ID));
    }

    @Test
    @DisplayName("getAggregateData — 날짜를 [00:00 ~ 23:59:59.999999999] 로 변환해 본인 범위로만 위임한다")
    void convertsDateRangeAndDelegatesWithinOwnScope() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        sut.getAggregateData(USER_ID, start, end);   // 레포 기본값(빈 결과)로 동작

        // 이벤트: 시작일 00:00 ~ 종료일 자정 직전(LocalTime.MAX) 일시 범위로 변환
        verify(calendarEventRepository).findByUserAndDateRange(
                USER_ID, start.atStartOfDay(), end.atTime(LocalTime.MAX));
        // 할일: dueDate 는 원본 날짜 그대로
        verify(todoRepository).findByUserAndDueDateBetween(USER_ID, start, end);
        // 거래: 카테고리/타입 필터 없이(null) 본인 기간 전체 조회
        verify(expenseRepository).findByUser(eq(USER_ID), isNull(), isNull(), eq(start), eq(end));
    }

    @Test
    @DisplayName("getAggregateData — 결과가 없으면 빈 리스트를 반환하고 리마인더 조회를 건너뛴다")
    void emptyResultsReturnEmptyListsAndSkipReminderLookup() {
        CalendarAggregateDto.AggregateData result =
                sut.getAggregateData(USER_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result.events()).isEmpty();
        assertThat(result.todos()).isEmpty();
        assertThat(result.expenses()).isEmpty();
        // 이벤트가 없으면 불필요한 리마인더 IN 조회를 하지 않는다(쿼리 절약)
        verify(eventReminderRepository, never()).findByEventIds(any());
    }

    @Test
    @DisplayName("getAggregateData — 이벤트는 있으나 리마인더가 없으면 각 이벤트의 리마인더는 빈 리스트")
    void eventsWithoutRemindersAttachEmptyReminderList() {
        given(calendarEventRepository.findByUserAndDateRange(any(), any(), any()))
                .willReturn(List.of(event(101L), event(102L)));
        given(eventReminderRepository.findByEventIds(any())).willReturn(List.of());

        CalendarAggregateDto.AggregateData result =
                sut.getAggregateData(USER_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events()).allSatisfy(e -> assertThat(e.reminders()).isEmpty());
    }
}
