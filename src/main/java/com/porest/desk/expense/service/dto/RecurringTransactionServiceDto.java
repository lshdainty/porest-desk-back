package com.porest.desk.expense.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class RecurringTransactionServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record UpdateCommand(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record RecurringInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextExecutionDate,
        LocalDateTime lastExecutedAt,
        YNType isActive,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static RecurringInfo from(RecurringTransaction recurring) {
            return new RecurringInfo(
                recurring.getRowId(),
                recurring.getUser().getRowId(),
                recurring.getCategory() != null ? recurring.getCategory().getRowId() : null,
                recurring.getCategory() != null ? recurring.getCategory().getCategoryName() : null,
                recurring.getAsset() != null ? recurring.getAsset().getRowId() : null,
                recurring.getAsset() != null ? recurring.getAsset().getAssetName() : null,
                recurring.getExpenseType(),
                recurring.getAmount(),
                recurring.getDescription(),
                recurring.getMerchant(),
                recurring.getPaymentMethod(),
                recurring.getFrequency(),
                recurring.getIntervalValue(),
                recurring.getDayOfWeek(),
                recurring.getDayOfMonth(),
                recurring.getStartDate(),
                recurring.getEndDate(),
                recurring.getNextExecutionDate(),
                recurring.getLastExecutedAt(),
                recurring.getIsActive(),
                recurring.getCreateAt(),
                recurring.getModifyAt()
            );
        }
    }
}
