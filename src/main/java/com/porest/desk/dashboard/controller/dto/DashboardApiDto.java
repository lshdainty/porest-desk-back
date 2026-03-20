package com.porest.desk.dashboard.controller.dto;

import com.porest.desk.dashboard.service.dto.DashboardServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardApiDto {
    public record SummaryResponse(
        TodoSummary todoSummary,
        CalendarSummary calendarSummary,
        ExpenseSummary expenseSummary,
        MemoSummary memoSummary,
        List<UpcomingEvent> upcomingEvents,
        List<RecentTodo> recentTodos,
        List<DailyExpenseTrend> expenseTrend
    ) {
        public static SummaryResponse from(DashboardServiceDto.DashboardSummary summary) {
            return new SummaryResponse(
                TodoSummary.from(summary.todoSummary()),
                CalendarSummary.from(summary.calendarSummary()),
                ExpenseSummary.from(summary.expenseSummary()),
                MemoSummary.from(summary.memoSummary()),
                summary.upcomingEvents().stream().map(UpcomingEvent::from).toList(),
                summary.recentTodos().stream().map(RecentTodo::from).toList(),
                summary.expenseTrend().stream().map(DailyExpenseTrend::from).toList()
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

    public record MemoSummary(long totalCount, long pinnedCount, String recentMemoTitle) {
        public static MemoSummary from(DashboardServiceDto.MemoSummary s) {
            return new MemoSummary(s.totalCount(), s.pinnedCount(), s.recentMemoTitle());
        }
    }

    public record UpcomingEvent(Long rowId, String title, String eventType, String color, LocalDateTime startDate, long daysUntil) {
        public static UpcomingEvent from(DashboardServiceDto.UpcomingEvent e) {
            return new UpcomingEvent(e.rowId(), e.title(), e.eventType(), e.color(), e.startDate(), e.daysUntil());
        }
    }

    public record RecentTodo(Long rowId, String title, String priority, String status, LocalDate dueDate) {
        public static RecentTodo from(DashboardServiceDto.RecentTodo t) {
            return new RecentTodo(t.rowId(), t.title(), t.priority(), t.status(), t.dueDate());
        }
    }

    public record DailyExpenseTrend(LocalDate date, long income, long expense) {
        public static DailyExpenseTrend from(DashboardServiceDto.DailyExpenseTrend t) {
            return new DailyExpenseTrend(t.date(), t.income(), t.expense());
        }
    }

    public record UpdateLayoutRequest(String dashboard) {}
    public record LayoutResponse(String dashboard) {}
}
