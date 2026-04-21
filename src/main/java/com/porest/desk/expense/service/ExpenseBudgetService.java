package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;

import java.util.List;

public interface ExpenseBudgetService {
    ExpenseBudgetServiceDto.BudgetInfo createBudget(ExpenseBudgetServiceDto.CreateCommand command);
    List<ExpenseBudgetServiceDto.BudgetInfo> getBudgets(Long userRowId, Integer year, Integer month);
    List<ExpenseBudgetServiceDto.ComplianceMonth> getCompliance(Long userRowId, Integer months);
    ExpenseBudgetServiceDto.BudgetInfo updateBudget(Long budgetId, Long userRowId, ExpenseBudgetServiceDto.UpdateCommand command);
    void deleteBudget(Long budgetId, Long userRowId);
}
