package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseService {
    ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command);
    List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, Long userRowId, ExpenseServiceDto.UpdateCommand command);
    void deleteExpense(Long expenseId, Long userRowId);
    ExpenseServiceDto.DailySummary getDailySummary(Long userRowId, LocalDate date);
    ExpenseServiceDto.MonthlySummary getMonthlySummary(Long userRowId, Integer year, Integer month);
    List<ExpenseServiceDto.MonthlyTrend> getMonthlyTrend(Long userRowId, Integer months);
    ExpenseServiceDto.WeeklySummary getWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd);
    ExpenseServiceDto.YearlySummary getYearlySummary(Long userRowId, Integer year);
    List<ExpenseServiceDto.MerchantSummary> getMerchantSummary(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.AssetSummary> getAssetSummary(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.ExpenseInfo> searchExpenses(ExpenseServiceDto.SearchCommand command);
    List<ExpenseServiceDto.ExpenseInfo> getGroupExpenses(Long userRowId, Long groupId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.ExpenseInfo> getExpensesByCalendarEvent(Long calendarEventRowId);
    List<ExpenseServiceDto.ExpenseInfo> getExpensesByTodo(Long todoRowId);
    List<ExpenseServiceDto.HeatmapCell> getHeatmap(Long userRowId, Integer year, Integer month);
}
