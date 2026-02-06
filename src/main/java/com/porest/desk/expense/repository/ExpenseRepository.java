package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Optional<Expense> findById(Long rowId);
    List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findDailySummary(Long userRowId, LocalDate date);
    List<Expense> findMonthlySummary(Long userRowId, Integer year, Integer month);
    Expense save(Expense expense);
    void delete(Expense expense);
}
