package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseService {
    ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command);
    List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, ExpenseServiceDto.UpdateCommand command);
    void deleteExpense(Long expenseId);
    ExpenseServiceDto.DailySummary getDailySummary(Long userRowId, LocalDate date);
    ExpenseServiceDto.MonthlySummary getMonthlySummary(Long userRowId, Integer year, Integer month);
}
