package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseBudget;

import java.util.List;
import java.util.Optional;

public interface ExpenseBudgetRepository {
    Optional<ExpenseBudget> findById(Long rowId);
    List<ExpenseBudget> findByUser(Long userRowId, Integer year, Integer month);
    List<ExpenseBudget> findAllByYearAndMonth(Integer year, Integer month);
    Optional<ExpenseBudget> findByUserAndCategory(Long userRowId, Long categoryRowId, Integer year, Integer month);
    ExpenseBudget save(ExpenseBudget expenseBudget);
    void delete(ExpenseBudget expenseBudget);
}
