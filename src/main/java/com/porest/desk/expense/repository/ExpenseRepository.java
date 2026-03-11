package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Optional<Expense> findById(Long rowId);
    List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findByGroups(List<Long> groupRowIds, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findDailySummary(Long userRowId, LocalDate date);
    List<Expense> findMonthlySummary(Long userRowId, Integer year, Integer month);
    List<Expense> findWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd);
    List<Expense> findYearlySummary(Long userRowId, Integer year);
    List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                         String keyword, String merchant, Long minAmount, Long maxAmount,
                         LocalDate startDate, LocalDate endDate);
    List<Expense> findByCalendarEvent(Long calendarEventRowId);
    List<Expense> findByTodo(Long todoRowId);
    Expense save(Expense expense);
    void delete(Expense expense);
}
