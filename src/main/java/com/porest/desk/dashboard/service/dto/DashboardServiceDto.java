package com.porest.desk.dashboard.service.dto;

import java.time.LocalDate;

public class DashboardServiceDto {
    public record DashboardSummary(
        TodoSummary todoSummary,
        CalendarSummary calendarSummary,
        ExpenseSummary expenseSummary,
        TimerSummary timerSummary,
        MemoSummary memoSummary
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
}
