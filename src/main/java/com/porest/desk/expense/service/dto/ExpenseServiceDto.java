package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ExpenseServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod
    ) {}

    public record UpdateCommand(
        Long categoryRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod
    ) {}

    public record ExpenseInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static ExpenseInfo from(Expense expense) {
            return new ExpenseInfo(
                expense.getRowId(),
                expense.getUser().getRowId(),
                expense.getCategory().getRowId(),
                expense.getCategory().getCategoryName(),
                expense.getExpenseType(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getPaymentMethod(),
                expense.getCreateAt(),
                expense.getModifyAt()
            );
        }
    }

    public record DailySummary(
        LocalDate date,
        Long totalIncome,
        Long totalExpense
    ) {}

    public record MonthlySummary(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense,
        List<CategoryBreakdown> categoryBreakdown
    ) {}

    public record CategoryBreakdown(
        Long categoryRowId,
        String categoryName,
        ExpenseType expenseType,
        Long totalAmount
    ) {}
}
