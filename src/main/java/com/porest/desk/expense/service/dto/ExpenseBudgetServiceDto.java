package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseBudget;

import java.time.LocalDateTime;

public class ExpenseBudgetServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        Long budgetAmount,
        Integer budgetYear,
        Integer budgetMonth
    ) {}

    public record ComplianceMonth(
        Integer year,
        Integer month,
        Long totalLimit,
        Long totalSpent,
        Double compliancePercent
    ) {}

    public record BudgetInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long budgetAmount,
        Integer budgetYear,
        Integer budgetMonth,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static BudgetInfo from(ExpenseBudget budget) {
            return new BudgetInfo(
                budget.getRowId(),
                budget.getUser().getRowId(),
                budget.getCategory() != null ? budget.getCategory().getRowId() : null,
                budget.getCategory() != null ? budget.getCategory().getCategoryName() : null,
                budget.getBudgetAmount(),
                budget.getBudgetYear(),
                budget.getBudgetMonth(),
                budget.getCreateAt(),
                budget.getModifyAt()
            );
        }
    }
}
