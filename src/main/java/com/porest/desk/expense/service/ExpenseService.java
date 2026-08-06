package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ExpenseService {
    ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command);

    /**
     * 대량 적재용 — 잔액 재산정과 예산 알림을 건너뛴다.
     * 재산정은 자산 전체 이력을 다시 읽으므로 행마다 하면 O(N²) 이고,
     * 예산 알림도 행 수만큼 발생한다. 호출자가 끝나고 자산별로 한 번만 재산정한다.
     */
    ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command, boolean bulk);

    /**
     * 대량 적재 — 여러 건을 한 트랜잭션에 넣는다. 한 건이라도 실패하면 전체 롤백되므로,
     * 호출자는 실패 시 건별 재시도로 문제 행을 가려내야 한다.
     */
    void createExpensesChunk(List<ExpenseServiceDto.CreateCommand> commands);
    List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, Long userRowId, ExpenseServiceDto.UpdateCommand command);
    void deleteExpense(Long expenseId, Long userRowId);
    ExpenseServiceDto.DailySummary getDailySummary(Long userRowId, LocalDate date);
    ExpenseServiceDto.RangeSummary getRangeSummary(Long userRowId, LocalDate startDate, LocalDate endDate);

    /** 자산으로 좁힌 기간 요약 — assetRowId 가 null 이면 전체. */
    ExpenseServiceDto.RangeSummary getRangeSummary(Long userRowId, LocalDate startDate, LocalDate endDate,
                                                  Long assetRowId);
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
