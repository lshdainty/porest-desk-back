package com.porest.desk.expense.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public class RecurringTransactionServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        Long sourceExpenseRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalTime executionTime,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxOccurrences,
        Boolean autoLog,
        Boolean notifyDayBefore
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
        LocalTime executionTime,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxOccurrences,
        Boolean autoLog,
        Boolean notifyDayBefore
    ) {}

    public record RecurringInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        Long sourceExpenseRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalTime executionTime,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxOccurrences,
        Integer executedCount,
        LocalDate nextExecutionDate,
        LocalDateTime lastExecutedAt,
        YNType isActive,
        boolean autoLog,
        boolean notifyDayBefore,
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
                recurring.getSourceExpense() != null ? recurring.getSourceExpense().getRowId() : null,
                recurring.getExpenseType(),
                recurring.getAmount(),
                recurring.getDescription(),
                recurring.getMerchant(),
                recurring.getPaymentMethod(),
                recurring.getFrequency(),
                recurring.getIntervalValue(),
                recurring.getDayOfWeek(),
                recurring.getDayOfMonth(),
                recurring.getExecutionTime(),
                recurring.getStartDate(),
                recurring.getEndDate(),
                recurring.getMaxOccurrences(),
                recurring.getExecutedCount(),
                recurring.getNextExecutionDate(),
                recurring.getLastExecutedAt(),
                recurring.getIsActive(),
                recurring.getAutoLog() == YNType.Y,
                recurring.getNotifyDayBefore() == YNType.Y,
                recurring.getCreateAt(),
                recurring.getModifyAt()
            );
        }
    }
}
