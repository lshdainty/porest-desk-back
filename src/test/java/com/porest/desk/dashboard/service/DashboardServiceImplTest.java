package com.porest.desk.dashboard.service;

import com.porest.core.exception.BusinessException;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.dashboard.service.dto.DashboardServiceDto;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 대시보드 종합 집계 로직 회귀 방지 단위 테스트.
 * getDashboardSummary 는 여러 도메인 레포를 한 번에 호출해 자바 메모리에서 필터·정렬·버킷 집계하므로,
 * 실제 엔티티로 레포 반환값을 구성(mock)하고 서비스가 만든 결과를 입력에서 독립 계산한 기대값과 대조한다.
 * 날짜 필터가 today(LocalDate.now()) 기준이므로 테스트 데이터도 today 상대값으로 만든다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private TodoRepository todoRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private DashboardServiceImpl sut;

    private static final long USER_ID = 1L;

    // ── entity builders ─────────────────────────────────────────────

    private Todo todoTask(long rowId, TodoStatus status, TodoPriority priority, String title, LocalDate dueDate) {
        Todo t = Todo.createTodo(null, title, null, priority, null, dueDate, null, TodoType.TASK);
        ReflectionTestUtils.setField(t, "rowId", rowId);
        ReflectionTestUtils.setField(t, "status", status);
        return t;
    }

    private Todo todoNote(long rowId, String title) {
        Todo n = Todo.createTodo(null, title, null, TodoPriority.MEDIUM, null, null, null, TodoType.NOTE);
        ReflectionTestUtils.setField(n, "rowId", rowId);
        return n;
    }

    private CalendarEvent event(long rowId, String title, CalendarEventType type, String color,
                                LocalDateTime start, LocalDateTime end) {
        CalendarEvent e = CalendarEvent.createEvent(null, title, null, type, color, start, end,
                null, null, null, null, null);
        ReflectionTestUtils.setField(e, "rowId", rowId);
        return e;
    }

    private Expense expense(ExpenseType type, long amount, LocalDateTime at) {
        return Expense.createExpense(null, null, null, type, amount, null, at, null, null, null);
    }

    private User userWithDashboard(String dashboard) {
        User u = User.createUser(10L, "u1", "홍길동", "u1@example.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        if (dashboard != null) u.updateDashboard(dashboard);
        return u;
    }

    /**
     * getDashboardSummary 가 호출하는 7개 레포 인터랙션의 기본 스텁(빈/0).
     * countStatsByUser 는 배열 인덱스 접근 때문에 반드시 길이 8 배열이 필요.
     * lenient 로 두어 각 테스트가 관심 있는 것만 given(...) 으로 덮어쓴다.
     */
    private void primeSummaryDefaults() {
        lenient().when(todoRepository.countStatsByUser(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(new long[]{0, 0, 0, 0, 0, 0, 0, 0});
        lenient().when(todoRepository.findAllByUser(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(TodoType.TASK))).thenReturn(List.of());
        lenient().when(todoRepository.findAllByUser(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(TodoType.NOTE))).thenReturn(List.of());
        lenient().when(calendarEventRepository.findByUserAndDateRange(eq(USER_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        lenient().when(expenseRepository.findDailySummary(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(List.of());
        lenient().when(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        lenient().when(expenseRepository.findByUser(eq(USER_ID), isNull(), isNull(),
                any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
    }

    // ── getDashboardSummary: todo & memo ────────────────────────────

    @Test
    @DisplayName("getDashboardSummary — countStatsByUser 배열 인덱스를 todo/memo 요약에 정확히 매핑 (memo는 [6],[7])")
    void todoAndMemoSummaryMapStatsArrayIndices() {
        primeSummaryDefaults();
        // [total, pending, inProgress, completed, todayDue, overDue, note, pinnedNote]
        given(todoRepository.countStatsByUser(eq(USER_ID), any(LocalDate.class)))
                .willReturn(new long[]{12, 5, 3, 4, 2, 9, 7, 2});
        given(todoRepository.findAllByUser(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(TodoType.NOTE)))
                .willReturn(List.of(todoNote(50L, "쇼핑 리스트"), todoNote(51L, "회의록")));

        DashboardServiceDto.DashboardSummary result = sut.getDashboardSummary(USER_ID);

        DashboardServiceDto.TodoSummary todo = result.todoSummary();
        assertThat(todo.totalCount()).isEqualTo(12L);
        assertThat(todo.pendingCount()).isEqualTo(5L);
        assertThat(todo.inProgressCount()).isEqualTo(3L);
        assertThat(todo.completedCount()).isEqualTo(4L);
        assertThat(todo.todayDueCount()).isEqualTo(2L);
        assertThat(todo.overDueCount()).isEqualTo(9L);        // stats[5]

        DashboardServiceDto.MemoSummary memo = result.memoSummary();
        assertThat(memo.totalCount()).isEqualTo(7L);          // stats[6]
        assertThat(memo.pinnedCount()).isEqualTo(2L);         // stats[7]
        assertThat(memo.recentMemoTitle()).isEqualTo("쇼핑 리스트"); // 첫 NOTE
    }

    @Test
    @DisplayName("getDashboardSummary — NOTE가 없으면 recentMemoTitle은 null")
    void recentMemoTitleNullWhenNoNotes() {
        primeSummaryDefaults();

        DashboardServiceDto.DashboardSummary result = sut.getDashboardSummary(USER_ID);

        assertThat(result.memoSummary().recentMemoTitle()).isNull();
    }

    @Test
    @DisplayName("getDashboardSummary — recentTodos: 완료 제외 + dueDate 오름차순(null 마지막)")
    void recentTodosExcludeCompletedSortNullsLast() {
        primeSummaryDefaults();
        LocalDate today = LocalDate.now();
        Todo completed = todoTask(1L, TodoStatus.COMPLETED, TodoPriority.HIGH, "완료됨", today);
        Todo a = todoTask(2L, TodoStatus.PENDING, TodoPriority.HIGH, "A", today.plusDays(1));
        Todo b = todoTask(3L, TodoStatus.IN_PROGRESS, TodoPriority.MEDIUM, "B", today.plusDays(2));
        Todo f = todoTask(4L, TodoStatus.PENDING, TodoPriority.LOW, "F", null);
        // 입력 순서 뒤섞음
        given(todoRepository.findAllByUser(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(TodoType.TASK))).willReturn(List.of(completed, f, b, a));

        List<DashboardServiceDto.RecentTodo> todos = sut.getDashboardSummary(USER_ID).recentTodos();

        assertThat(todos).hasSize(3);
        assertThat(todos).extracting(DashboardServiceDto.RecentTodo::rowId)
                .containsExactly(2L, 3L, 4L);            // A(+1), B(+2), F(null) — null 마지막
        assertThat(todos).extracting(DashboardServiceDto.RecentTodo::rowId).doesNotContain(1L); // 완료 제외
        // 필드 매핑 검증
        DashboardServiceDto.RecentTodo first = todos.get(0);
        assertThat(first.title()).isEqualTo("A");
        assertThat(first.priority()).isEqualTo("HIGH");
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.dueDate()).isEqualTo(today.plusDays(1));
        assertThat(todos.get(1).status()).isEqualTo("IN_PROGRESS");
        assertThat(todos.get(1).priority()).isEqualTo("MEDIUM");
        assertThat(todos.get(2).dueDate()).isNull();
    }

    @Test
    @DisplayName("getDashboardSummary — recentTodos는 최대 5개, 가장 늦은 항목이 잘린다")
    void recentTodosLimitedToFive() {
        primeSummaryDefaults();
        LocalDate today = LocalDate.now();
        Todo t11 = todoTask(11L, TodoStatus.PENDING, TodoPriority.LOW, "d1", today.plusDays(1));
        Todo t12 = todoTask(12L, TodoStatus.PENDING, TodoPriority.LOW, "d2", today.plusDays(2));
        Todo t13 = todoTask(13L, TodoStatus.PENDING, TodoPriority.LOW, "d3", today.plusDays(3));
        Todo t14 = todoTask(14L, TodoStatus.PENDING, TodoPriority.LOW, "d4", today.plusDays(4));
        Todo t15 = todoTask(15L, TodoStatus.PENDING, TodoPriority.LOW, "d5", today.plusDays(5));
        Todo t16 = todoTask(16L, TodoStatus.PENDING, TodoPriority.LOW, "d6", today.plusDays(6));
        given(todoRepository.findAllByUser(eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(TodoType.TASK)))
                .willReturn(List.of(t16, t13, t11, t15, t12, t14));

        List<DashboardServiceDto.RecentTodo> todos = sut.getDashboardSummary(USER_ID).recentTodos();

        assertThat(todos).hasSize(5);
        assertThat(todos).extracting(DashboardServiceDto.RecentTodo::rowId)
                .containsExactly(11L, 12L, 13L, 14L, 15L); // +1..+5 오름차순
        assertThat(todos).extracting(DashboardServiceDto.RecentTodo::rowId)
                .doesNotContain(16L);                      // 가장 늦은(+6) 항목 잘림
    }

    // ── getDashboardSummary: expense ────────────────────────────────

    @Test
    @DisplayName("getDashboardSummary — 오늘/이번달 수입·지출을 타입별로 각각 합산")
    void expenseTodayAndMonthlySums() {
        primeSummaryDefaults();
        LocalDateTime anyTime = LocalDateTime.now();
        given(expenseRepository.findDailySummary(eq(USER_ID), any(LocalDate.class)))
                .willReturn(List.of(
                        expense(ExpenseType.INCOME, 100_000L, anyTime),
                        expense(ExpenseType.EXPENSE, 30_000L, anyTime),
                        expense(ExpenseType.EXPENSE, 12_000L, anyTime)));
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        expense(ExpenseType.INCOME, 500_000L, anyTime),
                        expense(ExpenseType.INCOME, 200_000L, anyTime),
                        expense(ExpenseType.EXPENSE, 150_000L, anyTime),
                        expense(ExpenseType.EXPENSE, 50_000L, anyTime)));

        DashboardServiceDto.ExpenseSummary exp = sut.getDashboardSummary(USER_ID).expenseSummary();

        assertThat(exp.todayIncome()).isEqualTo(100_000L);
        assertThat(exp.todayExpense()).isEqualTo(42_000L);        // 30,000 + 12,000
        assertThat(exp.monthlyIncome()).isEqualTo(700_000L);      // 500,000 + 200,000
        assertThat(exp.monthlyExpense()).isEqualTo(200_000L);     // 150,000 + 50,000
    }

    // ── getDashboardSummary: calendar ───────────────────────────────

    @Test
    @DisplayName("getDashboardSummary — todayEventCount(겹침)·upcomingEventCount(원본크기)·nextEventDate·upcoming목록을 각각 정확히 산출")
    void calendarTodayCountUpcomingAndNextDate() {
        primeSummaryDefaults();
        LocalDate today = LocalDate.now();
        // 어제 시작~내일 끝 여러날 이벤트: 오늘과 겹침 O, 하지만 시작일(어제)이 today 이전 → upcoming/next에서 제외
        CalendarEvent multiDay = event(101L, "여러날", CalendarEventType.WORK, "#111111",
                today.minusDays(1).atStartOfDay(), today.plusDays(1).atTime(23, 0));
        CalendarEvent todayEvent = event(102L, "오늘", CalendarEventType.PERSONAL, "#ff0000",
                today.atTime(10, 0), today.atTime(11, 0));
        CalendarEvent in2Days = event(103L, "이틀뒤", CalendarEventType.BIRTHDAY, "#00ff00",
                today.plusDays(2).atTime(9, 0), today.plusDays(2).atTime(10, 0));
        CalendarEvent in5Days = event(104L, "닷새뒤", CalendarEventType.HOLIDAY, "#0000ff",
                today.plusDays(5).atTime(9, 0), today.plusDays(5).atTime(10, 0));
        // 정렬 검증 위해 뒤섞어 반환
        given(calendarEventRepository.findByUserAndDateRange(eq(USER_ID),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(in5Days, multiDay, in2Days, todayEvent));

        DashboardServiceDto.DashboardSummary result = sut.getDashboardSummary(USER_ID);
        DashboardServiceDto.CalendarSummary cal = result.calendarSummary();

        assertThat(cal.todayEventCount()).isEqualTo(2L);          // multiDay(겹침) + todayEvent
        assertThat(cal.upcomingEventCount()).isEqualTo(4L);       // 반환 원본 크기
        assertThat(cal.nextEventDate()).isEqualTo(today);         // 시작일 today 이상 중 최솟값(오늘)

        List<DashboardServiceDto.UpcomingEvent> upcoming = result.upcomingEvents();
        assertThat(upcoming).hasSize(3);                          // multiDay(어제 시작) 제외
        assertThat(upcoming).extracting(DashboardServiceDto.UpcomingEvent::rowId)
                .containsExactly(102L, 103L, 104L);              // 시작시각 오름차순
        assertThat(upcoming).extracting(DashboardServiceDto.UpcomingEvent::daysUntil)
                .containsExactly(0L, 2L, 5L);
        DashboardServiceDto.UpcomingEvent firstUp = upcoming.get(0);
        assertThat(firstUp.title()).isEqualTo("오늘");
        assertThat(firstUp.eventType()).isEqualTo("PERSONAL");
        assertThat(firstUp.color()).isEqualTo("#ff0000");
        assertThat(firstUp.startDate()).isEqualTo(today.atTime(10, 0));
    }

    @Test
    @DisplayName("getDashboardSummary — upcomingEvents는 시작 오름차순 최대 5개")
    void upcomingEventsLimitedToFive() {
        primeSummaryDefaults();
        LocalDate today = LocalDate.now();
        CalendarEvent e1 = event(1L, "d1", CalendarEventType.PERSONAL, "#1", today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(10, 0));
        CalendarEvent e2 = event(2L, "d2", CalendarEventType.PERSONAL, "#2", today.plusDays(2).atTime(9, 0), today.plusDays(2).atTime(10, 0));
        CalendarEvent e3 = event(3L, "d3", CalendarEventType.PERSONAL, "#3", today.plusDays(3).atTime(9, 0), today.plusDays(3).atTime(10, 0));
        CalendarEvent e4 = event(4L, "d4", CalendarEventType.PERSONAL, "#4", today.plusDays(4).atTime(9, 0), today.plusDays(4).atTime(10, 0));
        CalendarEvent e5 = event(5L, "d5", CalendarEventType.PERSONAL, "#5", today.plusDays(5).atTime(9, 0), today.plusDays(5).atTime(10, 0));
        CalendarEvent e6 = event(6L, "d6", CalendarEventType.PERSONAL, "#6", today.plusDays(6).atTime(9, 0), today.plusDays(6).atTime(10, 0));
        given(calendarEventRepository.findByUserAndDateRange(eq(USER_ID),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of(e6, e3, e1, e5, e2, e4));

        List<DashboardServiceDto.UpcomingEvent> upcoming = sut.getDashboardSummary(USER_ID).upcomingEvents();

        assertThat(upcoming).hasSize(5);
        assertThat(upcoming).extracting(DashboardServiceDto.UpcomingEvent::rowId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);            // +1..+5
        assertThat(upcoming).extracting(DashboardServiceDto.UpcomingEvent::rowId)
                .doesNotContain(6L);                             // 가장 늦은 항목 잘림
    }

    // ── getDashboardSummary: expense trend ──────────────────────────

    @Test
    @DisplayName("getDashboardSummary — 30일 추이: 경계 포함, 창밖 제외, 같은날 타입별 누적, 오름차순 0채움")
    void expenseTrendBucketsZeroFillsAndWindow() {
        primeSummaryDefaults();
        LocalDate today = LocalDate.now();
        given(expenseRepository.findByUser(eq(USER_ID), isNull(), isNull(),
                any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        expense(ExpenseType.INCOME, 10_000L, today.atTime(12, 0)),        // 오늘 수입
                        expense(ExpenseType.EXPENSE, 4_000L, today.atTime(15, 0)),        // 오늘 지출(같은날 누적)
                        expense(ExpenseType.EXPENSE, 2_000L, today.minusDays(1).atStartOfDay()), // 어제 지출
                        expense(ExpenseType.INCOME, 7_000L, today.minusDays(29).atTime(9, 0)),   // 창 시작 경계(포함)
                        expense(ExpenseType.EXPENSE, 99_999L, today.minusDays(30).atTime(9, 0)), // 창밖(이전) → 제외
                        expense(ExpenseType.INCOME, 88_888L, today.plusDays(1).atTime(9, 0))));  // 창밖(미래) → 제외

        List<DashboardServiceDto.DailyExpenseTrend> trend = sut.getDashboardSummary(USER_ID).expenseTrend();

        assertThat(trend).hasSize(30);                           // today-29 .. today
        assertThat(trend.get(0).date()).isEqualTo(today.minusDays(29));
        assertThat(trend.get(0).income()).isEqualTo(7_000L);
        assertThat(trend.get(0).expense()).isZero();
        assertThat(trend.get(29).date()).isEqualTo(today);
        assertThat(trend.get(29).income()).isEqualTo(10_000L);
        assertThat(trend.get(29).expense()).isEqualTo(4_000L);
        assertThat(trend.get(28).date()).isEqualTo(today.minusDays(1));
        assertThat(trend.get(28).income()).isZero();
        assertThat(trend.get(28).expense()).isEqualTo(2_000L);
        // 창밖 거래 제외 확인: 전체 합은 경계·창내 값만
        assertThat(trend.stream().mapToLong(DashboardServiceDto.DailyExpenseTrend::income).sum())
                .isEqualTo(17_000L);                             // 7,000 + 10,000 (88,888 제외)
        assertThat(trend.stream().mapToLong(DashboardServiceDto.DailyExpenseTrend::expense).sum())
                .isEqualTo(6_000L);                              // 4,000 + 2,000 (99,999 제외)
        // 오름차순 정렬 확인
        for (int i = 1; i < trend.size(); i++) {
            assertThat(trend.get(i).date()).isAfter(trend.get(i - 1).date());
        }
    }

    @Test
    @DisplayName("getDashboardSummary — 데이터가 전부 비면 0/빈목록, 추이는 30일 0채움")
    void emptyDataProducesZerosAndEmptyLists() {
        primeSummaryDefaults(); // 전부 0/빈 기본값

        DashboardServiceDto.DashboardSummary result = sut.getDashboardSummary(USER_ID);

        assertThat(result.todoSummary().totalCount()).isZero();
        assertThat(result.todoSummary().pendingCount()).isZero();
        assertThat(result.memoSummary().totalCount()).isZero();
        assertThat(result.memoSummary().recentMemoTitle()).isNull();
        assertThat(result.calendarSummary().todayEventCount()).isZero();
        assertThat(result.calendarSummary().upcomingEventCount()).isZero();
        assertThat(result.calendarSummary().nextEventDate()).isNull();
        assertThat(result.expenseSummary().todayIncome()).isZero();
        assertThat(result.expenseSummary().monthlyExpense()).isZero();
        assertThat(result.upcomingEvents()).isEmpty();
        assertThat(result.recentTodos()).isEmpty();
        assertThat(result.expenseTrend()).hasSize(30);
        assertThat(result.expenseTrend()).allSatisfy(d -> {
            assertThat(d.income()).isZero();
            assertThat(d.expense()).isZero();
        });
    }

    // ── getDashboardLayout ──────────────────────────────────────────

    @Test
    @DisplayName("getDashboardLayout — 사용자의 dashboard 문자열을 그대로 반환")
    void getDashboardLayoutReturnsUserDashboard() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(userWithDashboard("{\"layout\":\"grid\"}")));

        assertThat(sut.getDashboardLayout(USER_ID)).isEqualTo("{\"layout\":\"grid\"}");
    }

    @Test
    @DisplayName("getDashboardLayout — 사용자가 없으면 USER_NOT_FOUND")
    void getDashboardLayoutThrowsWhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getDashboardLayout(USER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.USER_NOT_FOUND);
    }

    // ── updateDashboardLayout ───────────────────────────────────────

    @Test
    @DisplayName("updateDashboardLayout — 엔티티 dashboard를 갱신하고 갱신값을 반환")
    void updateDashboardLayoutMutatesAndReturns() {
        User user = userWithDashboard(null);       // 초기 dashboard 없음
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        String returned = sut.updateDashboardLayout(USER_ID, "{\"layout\":\"list\"}");

        assertThat(returned).isEqualTo("{\"layout\":\"list\"}");
        assertThat(user.getDashboard()).isEqualTo("{\"layout\":\"list\"}"); // 엔티티에 반영
    }

    @Test
    @DisplayName("updateDashboardLayout — 사용자가 없으면 USER_NOT_FOUND")
    void updateDashboardLayoutThrowsWhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateDashboardLayout(USER_ID, "{}"))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.USER_NOT_FOUND);
    }
}
