package com.porest.desk.dashboard.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardServiceDto {
    public record DashboardSummary(
        TodoSummary todoSummary,
        CalendarSummary calendarSummary,
        ExpenseSummary expenseSummary,
        TimerSummary timerSummary,
        MemoSummary memoSummary,
        List<UpcomingEvent> upcomingEvents,
        List<RecentTodo> recentTodos,
        List<DailyExpenseTrend> expenseTrend
    ) {}

    public record TodoSummary(
        long totalCount,
        long pendingCount,
        long inProgressCount,
        long completedCount,
        long todayDueCount
    ) {}

    public record CalendarSummary(
        long todayEventCount,
        long upcomingEventCount,
        LocalDate nextEventDate
    ) {}

    public record ExpenseSummary(
        long todayIncome,
        long todayExpense,
        long monthlyIncome,
        long monthlyExpense
    ) {}

    public record TimerSummary(
        long todayFocusSeconds,
        long todaySessionCount,
        long weeklyFocusSeconds
    ) {}

    public record MemoSummary(
        long totalCount,
        long pinnedCount,
        String recentMemoTitle
    ) {}

    public record UpcomingEvent(
        Long rowId,
        String title,
        String eventType,
        String color,
        LocalDateTime startDate,
        long daysUntil
    ) {}

    public record RecentTodo(
        Long rowId,
        String title,
        String priority,
        String status,
        LocalDate dueDate
    ) {}

    public record DailyExpenseTrend(
        LocalDate date,
        long income,
        long expense
    ) {}
}
