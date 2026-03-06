package com.porest.desk.dashboard.service;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.dashboard.service.dto.DashboardServiceDto;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.repository.TimerSessionRepository;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final TodoRepository todoRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ExpenseRepository expenseRepository;
    private final TimerSessionRepository timerSessionRepository;

    @Override
    public DashboardServiceDto.DashboardSummary getDashboardSummary(Long userRowId) {
        log.debug("대시보드 요약 조회: userRowId={}", userRowId);

        LocalDate today = LocalDate.now();

        // Todo summary
        List<Todo> allTodos = todoRepository.findAllByUser(userRowId, null, null, null, null, null, null, null);
        List<Todo> taskTodos = allTodos.stream().filter(t -> t.getType() == TodoType.TASK).toList();
        long pendingCount = taskTodos.stream().filter(t -> t.getStatus() == TodoStatus.PENDING).count();
        long inProgressCount = taskTodos.stream().filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS).count();
        long completedCount = taskTodos.stream().filter(t -> t.getStatus() == TodoStatus.COMPLETED).count();
        long todayDueCount = taskTodos.stream().filter(t -> today.equals(t.getDueDate())).count();

        // Calendar summary
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        List<CalendarEvent> todayEvents = calendarEventRepository.findByUserAndDateRange(userRowId, todayStart, todayEnd);
        LocalDateTime weekEnd = today.plusDays(7).atTime(LocalTime.MAX);
        List<CalendarEvent> upcomingEvents = calendarEventRepository.findByUserAndDateRange(userRowId, todayStart, weekEnd);
        LocalDate nextEventDate = upcomingEvents.stream()
            .map(e -> e.getStartDate().toLocalDate())
            .filter(d -> !d.isBefore(today))
            .min(LocalDate::compareTo)
            .orElse(null);

        // Expense summary
        List<Expense> todayExpenses = expenseRepository.findDailySummary(userRowId, today);
        long todayIncome = todayExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.INCOME).mapToLong(Expense::getAmount).sum();
        long todayExpenseAmount = todayExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.EXPENSE).mapToLong(Expense::getAmount).sum();
        List<Expense> monthExpenses = expenseRepository.findMonthlySummary(userRowId, today.getYear(), today.getMonthValue());
        long monthlyIncome = monthExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.INCOME).mapToLong(Expense::getAmount).sum();
        long monthlyExpenseAmount = monthExpenses.stream().filter(e -> e.getExpenseType() == ExpenseType.EXPENSE).mapToLong(Expense::getAmount).sum();

        // Timer summary
        List<TimerSession> todaySessions = timerSessionRepository.findDailyStats(userRowId, today, today);
        long todayFocusSeconds = todaySessions.stream().mapToLong(TimerSession::getDurationSeconds).sum();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        List<TimerSession> weekSessions = timerSessionRepository.findDailyStats(userRowId, weekStart, today);
        long weeklyFocusSeconds = weekSessions.stream().mapToLong(TimerSession::getDurationSeconds).sum();

        // Memo summary (based on todo type=NOTE)
        List<Todo> noteTodos = allTodos.stream().filter(t -> t.getType() == TodoType.NOTE).toList();
        long pinnedCount = noteTodos.stream().filter(t -> t.getIsPinned() == YNType.Y).count();
        String recentMemoTitle = noteTodos.isEmpty() ? null : noteTodos.get(0).getTitle();

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

        // Recent todos (incomplete TASK type only, sorted by due date, max 5)
        List<DashboardServiceDto.RecentTodo> recentTodoList = taskTodos.stream()
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

        // Expense trend (last 30 days, daily income/expense)
        LocalDate trendStart = today.minusDays(29);
        List<Expense> trendExpenses = expenseRepository.findByUser(userRowId, null, null, trendStart, today);
        Map<LocalDate, long[]> dailyMap = new TreeMap<>();
        for (LocalDate d = trendStart; !d.isAfter(today); d = d.plusDays(1)) {
            dailyMap.put(d, new long[]{0, 0});
        }
        for (Expense e : trendExpenses) {
            long[] amounts = dailyMap.get(e.getExpenseDate());
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
        var todoSummary = new DashboardServiceDto.TodoSummary(taskTodos.size(), pendingCount, inProgressCount, completedCount, todayDueCount);
        var calendarSummary = new DashboardServiceDto.CalendarSummary(todayEvents.size(), upcomingEvents.size(), nextEventDate);
        var expenseSummary = new DashboardServiceDto.ExpenseSummary(todayIncome, todayExpenseAmount, monthlyIncome, monthlyExpenseAmount);
        var timerSummary = new DashboardServiceDto.TimerSummary(todayFocusSeconds, todaySessions.size(), weeklyFocusSeconds);
        var memoSummary = new DashboardServiceDto.MemoSummary(noteTodos.size(), pinnedCount, recentMemoTitle);

        log.debug("대시보드 요약 조회 완료: userRowId={}", userRowId);

        return new DashboardServiceDto.DashboardSummary(
            todoSummary, calendarSummary, expenseSummary, timerSummary, memoSummary,
            upcomingEventList, recentTodoList, expenseTrendList
        );
    }
}
