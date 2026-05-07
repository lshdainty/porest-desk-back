package com.porest.desk.dashboard.service;

import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.dashboard.service.dto.DashboardServiceDto;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final TodoRepository todoRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Override
    public DashboardServiceDto.DashboardSummary getDashboardSummary(Long userRowId) {
        log.debug("대시보드 요약 조회: userRowId={}", userRowId);

        LocalDate today = LocalDate.now();

        // Todo & Memo summary — 단일 집계 쿼리로 모든 카운트 조회 (전체 엔티티 로드 X)
        long[] stats = todoRepository.countStatsByUser(userRowId, today);
        // [0]=totalTask, [1]=pending, [2]=inProgress, [3]=completed, [4]=todayDue, [5]=overDue, [6]=noteCount, [7]=pinnedNoteCount

        // 최근 미완료 TASK 5개만 조회 (전체 로드 대신 필터된 쿼리)
        List<Todo> recentIncompleteTasks = todoRepository.findAllByUser(userRowId, null, null, null, null, null, null, TodoType.TASK);
        List<DashboardServiceDto.RecentTodo> recentTodoList = recentIncompleteTasks.stream()
            .filter(t -> t.getStatus() != TodoStatus.COMPLETED)
            .sorted(Comparator.comparing(Todo::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(5)
            .map(t -> new DashboardServiceDto.RecentTodo(
                t.getRowId(),
                t.getTitle(),
                t.getPriority().name(),
                t.getStatus().name(),
                t.getDueDate()
            ))
            .toList();

        // 최근 메모 제목 — NOTE 타입 1개만 조회
        List<Todo> recentNotes = todoRepository.findAllByUser(userRowId, null, null, null, null, null, null, TodoType.NOTE);
        String recentMemoTitle = recentNotes.isEmpty() ? null : recentNotes.get(0).getTitle();

        // Calendar summary — 1주일 범위 한번만 쿼리, 오늘 이벤트는 메모리에서 필터
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekEnd = today.plusDays(7).atTime(LocalTime.MAX);
        List<CalendarEvent> upcomingEvents = calendarEventRepository.findByUserAndDateRange(userRowId, todayStart, weekEnd);

        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        long todayEventCount = upcomingEvents.stream()
            .filter(e -> !e.getStartDate().isAfter(todayEnd) && !e.getEndDate().isBefore(todayStart))
            .count();

        LocalDate nextEventDate = upcomingEvents.stream()
            .map(e -> e.getStartDate().toLocalDate())
            .filter(d -> !d.isBefore(today))
            .min(LocalDate::compareTo)
            .orElse(null);

        // Expense summary
        List<Expense> todayExpenses = expenseRepository.findDailySummary(userRowId, today);
        long todayIncome = todayExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.INCOME).mapToLong(Expense::getAmount).sum();
        long todayExpenseAmount = todayExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.EXPENSE).mapToLong(Expense::getAmount).sum();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<Expense> monthExpenses = expenseRepository.findByDateRange(userRowId, monthStart, monthEnd);
        long monthlyIncome = monthExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.INCOME).mapToLong(Expense::getAmount).sum();
        long monthlyExpenseAmount = monthExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.EXPENSE).mapToLong(Expense::getAmount).sum();

        // Upcoming events (next 7 days, max 5)
        List<DashboardServiceDto.UpcomingEvent> upcomingEventList = upcomingEvents.stream()
            .filter(e -> !e.getStartDate().toLocalDate().isBefore(today))
            .sorted(Comparator.comparing(CalendarEvent::getStartDate))
            .limit(5)
            .map(e -> new DashboardServiceDto.UpcomingEvent(
                e.getRowId(),
                e.getTitle(),
                e.getEventType().name(),
                e.getColor(),
                e.getStartDate(),
                ChronoUnit.DAYS.between(today, e.getStartDate().toLocalDate())
            ))
            .toList();

        // Expense trend (last 30 days, daily income/expense)
        LocalDate trendStart = today.minusDays(29);
        List<Expense> trendExpenses = expenseRepository.findByUser(userRowId, null, null, trendStart, today);
        Map<LocalDate, long[]> dailyMap = new TreeMap<>();
        for (LocalDate d = trendStart; !d.isAfter(today); d = d.plusDays(1)) {
            dailyMap.put(d, new long[]{0, 0});
        }
        for (Expense e : trendExpenses) {
            // expenseDate 는 LocalDateTime 이므로 LocalDate 키로 변환
            long[] amounts = dailyMap.get(e.getExpenseDate().toLocalDate());
            if (amounts != null) {
                if (e.getExpenseType() == ExpenseType.INCOME) {
                    amounts[0] += e.getAmount();
                } else {
                    amounts[1] += e.getAmount();
                }
            }
        }
        List<DashboardServiceDto.DailyExpenseTrend> expenseTrendList = dailyMap.entrySet().stream()
            .map(entry -> new DashboardServiceDto.DailyExpenseTrend(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .toList();

        // Build result
        var todoSummary = new DashboardServiceDto.TodoSummary(stats[0], stats[1], stats[2], stats[3], stats[4]);
        var calendarSummary = new DashboardServiceDto.CalendarSummary(todayEventCount, upcomingEvents.size(), nextEventDate);
        var expenseSummary = new DashboardServiceDto.ExpenseSummary(todayIncome, todayExpenseAmount, monthlyIncome, monthlyExpenseAmount);
        var memoSummary = new DashboardServiceDto.MemoSummary(stats[6], stats[7], recentMemoTitle);

        log.debug("대시보드 요약 조회 완료: userRowId={}", userRowId);

        return new DashboardServiceDto.DashboardSummary(
            todoSummary, calendarSummary, expenseSummary, memoSummary,
            upcomingEventList, recentTodoList, expenseTrendList
        );
    }

    @Override
    public String getDashboardLayout(Long userRowId) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        return user.getDashboard();
    }

    @Override
    @Transactional
    public String updateDashboardLayout(Long userRowId, String dashboard) {
        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        user.updateDashboard(dashboard);
        return user.getDashboard();
    }
}
