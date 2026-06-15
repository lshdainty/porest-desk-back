package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseBudget;

import java.util.List;
import java.util.Optional;

public interface ExpenseBudgetRepository {
    Optional<ExpenseBudget> findById(Long rowId);
    List<ExpenseBudget> findByUser(Long userRowId, Integer year, Integer month);
    List<ExpenseBudget> findAllByYearAndMonth(Integer year, Integer month);
    Optional<ExpenseBudget> findByUserAndCategory(Long userRowId, Long categoryRowId, Integer year, Integer month);
    /** 카테고리에 걸린 모든 월의 예산 (카테고리 삭제 시 cascade 정리용). */
    List<ExpenseBudget> findAllByCategory(Long categoryRowId);
    ExpenseBudget save(ExpenseBudget expenseBudget);
    void delete(ExpenseBudget expenseBudget);
}
