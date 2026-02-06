package com.porest.desk.dashboard.controller.dto;

import com.porest.desk.dashboard.service.dto.DashboardServiceDto;

import java.time.LocalDate;

public class DashboardApiDto {
    public record SummaryResponse(
        TodoSummary todoSummary,
        CalendarSummary calendarSummary,
        ExpenseSummary expenseSummary,
        TimerSummary timerSummary,
        MemoSummary memoSummary
    ) {
        public static SummaryResponse from(DashboardServiceDto.DashboardSummary summary) {
            return new SummaryResponse(
                TodoSummary.from(summary.todoSummary()),
                CalendarSummary.from(summary.calendarSummary()),
                ExpenseSummary.from(summary.expenseSummary()),
                TimerSummary.from(summary.timerSummary()),
                MemoSummary.from(summary.memoSummary())
            );
        }
    }

    public record TodoSummary(long totalCount, long pendingCount, long inProgressCount, long completedCount, long todayDueCount) {
        public static TodoSummary from(DashboardServiceDto.TodoSummary s) {
            return new TodoSummary(s.totalCount(), s.pendingCount(), s.inProgressCount(), s.completedCount(), s.todayDueCount());
        }
    }

    public record CalendarSummary(long todayEventCount, long upcomingEventCount, LocalDate nextEventDate) {
        public static CalendarSummary from(DashboardServiceDto.CalendarSummary s) {
            return new CalendarSummary(s.todayEventCount(), s.upcomingEventCount(), s.nextEventDate());
        }
    }

    public record ExpenseSummary(long todayIncome, long todayExpense, long monthlyIncome, long monthlyExpense) {
        public static ExpenseSummary from(DashboardServiceDto.ExpenseSummary s) {
            return new ExpenseSummary(s.todayIncome(), s.todayExpense(), s.monthlyIncome(), s.monthlyExpense());
        }
    }

    public record TimerSummary(long todayFocusSeconds, long todaySessionCount, long weeklyFocusSeconds) {
        public static TimerSummary from(DashboardServiceDto.TimerSummary s) {
            return new TimerSummary(s.todayFocusSeconds(), s.todaySessionCount(), s.weeklyFocusSeconds());
        }
    }

    public record MemoSummary(long totalCount, long pinnedCount, String recentMemoTitle) {
        public static MemoSummary from(DashboardServiceDto.MemoSummary s) {
            return new MemoSummary(s.totalCount(), s.pinnedCount(), s.recentMemoTitle());
        }
    }
}
