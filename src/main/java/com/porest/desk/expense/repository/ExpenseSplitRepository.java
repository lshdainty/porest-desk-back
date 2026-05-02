package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseSplit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseSplitRepository {
    Optional<ExpenseSplit> findById(Long rowId);
    List<ExpenseSplit> findByExpense(Long expenseRowId);
    List<ExpenseSplit> findByExpenseIds(List<Long> expenseRowIds);
    ExpenseSplit save(ExpenseSplit split);
    void deleteByExpense(Long expenseRowId);

    /**
     * 사용자의 월별 분할 합계를 카테고리별로 한 번에 조회.
     * 반환 Object[] = { Long categoryRowId, ExpenseType expenseType, Long totalAmount }
     * MonthlySummary 카테고리 집계에서 부모 expense 의 단일 카테고리 대신 분할 항목별로 사용.
     */
    List<Object[]> sumMonthlyByUserGroupedByCategoryAndType(Long userRowId, LocalDate startDate, LocalDate endDate);
}
