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
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId
    ) {}

    public record UpdateCommand(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId
    ) {}

    public record ExpenseInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static ExpenseInfo from(Expense expense) {
            return new ExpenseInfo(
                expense.getRowId(),
                expense.getUser().getRowId(),
                expense.getCategory().getRowId(),
                expense.getCategory().getCategoryName(),
                expense.getAsset() != null ? expense.getAsset().getRowId() : null,
                expense.getAsset() != null ? expense.getAsset().getAssetName() : null,
                expense.getExpenseType(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getMerchant(),
                expense.getPaymentMethod(),
                expense.getCalendarEvent() != null ? expense.getCalendarEvent().getRowId() : null,
                expense.getTodo() != null ? expense.getTodo().getRowId() : null,
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
        Long parentCategoryRowId,
        String parentCategoryName,
        ExpenseType expenseType,
        Long totalAmount
    ) {}

    public record WeeklySummary(
        LocalDate weekStart,
        LocalDate weekEnd,
        Long totalIncome,
        Long totalExpense
    ) {}

    public record YearlySummary(
        Integer year,
        Long totalIncome,
        Long totalExpense,
        List<MonthlyAmount> monthlyAmounts
    ) {}

    public record MonthlyAmount(
        Integer month,
        Long totalIncome,
        Long totalExpense
    ) {}

    public record MerchantSummary(
        String merchant,
        Long totalAmount,
        Integer count
    ) {}

    public record AssetSummary(
        Long assetRowId,
        String assetName,
        Long totalAmount,
        Integer count
    ) {}

    public record SearchCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        String keyword,
        String merchant,
        Long minAmount,
        Long maxAmount,
        LocalDate startDate,
        LocalDate endDate
    ) {}
}
