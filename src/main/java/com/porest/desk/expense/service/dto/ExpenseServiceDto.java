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
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId
    ) {}

    public record UpdateCommand(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId
    ) {}

    public record ExpenseInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId,
        String groupName,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static ExpenseInfo from(Expense expense) {
            return new ExpenseInfo(
                expense.getRowId(),
                expense.getUser().getRowId(),
                expense.getCategory().getRowId(),
                expense.getCategory().getCategoryName(),
                expense.getCategory().getIcon(),
                expense.getCategory().getColor(),
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
                expense.getGroup() != null ? expense.getGroup().getRowId() : null,
                expense.getGroup() != null ? expense.getGroup().getGroupName() : null,
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

    public record MonthlyTrend(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense
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
        Long totalExpense,
        List<CategoryBreakdown> categoryBreakdown
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

    public record HeatmapCell(
        Integer dayOfWeek,
        Integer hour,
        Long totalAmount
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
