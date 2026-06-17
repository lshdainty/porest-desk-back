package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ExpenseService {
    ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command);
    List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, Long userRowId, ExpenseServiceDto.UpdateCommand command);
    void deleteExpense(Long expenseId, Long userRowId);
    ExpenseServiceDto.DailySummary getDailySummary(Long userRowId, LocalDate date);
    ExpenseServiceDto.RangeSummary getRangeSummary(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.MonthlyTrend> getMonthlyTrend(Long userRowId, Integer months);
    List<ExpenseServiceDto.MerchantSummary> getMerchantSummary(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.AssetSummary> getAssetSummary(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<ExpenseServiceDto.ExpenseInfo> searchExpenses(ExpenseServiceDto.SearchCommand command);
    List<ExpenseServiceDto.ExpenseInfo> getExpensesByCalendarEvent(Long calendarEventRowId);
    List<ExpenseServiceDto.ExpenseInfo> getExpensesByTodo(Long todoRowId);
    List<ExpenseServiceDto.HeatmapCell> getHeatmap(Long userRowId, LocalDate startDate, LocalDate endDate);

    /**
     * 해당 월의 EXPENSE 지출을 카테고리별로 집계해 반환(split-aware).
     * 분할이 있는 거래는 분할 항목별 카테고리로, 없으면 거래 카테고리로 귀속하며,
     * 각 leaf 금액을 leaf 키와 부모 키 모두에 누적(롤업)한다. 예산 알림 등 카테고리 귀속이 필요한 곳에서 사용.
     */
    Map<Long, Long> getMonthlyExpenseSpendByCategory(Long userRowId, int year, int month);
}
