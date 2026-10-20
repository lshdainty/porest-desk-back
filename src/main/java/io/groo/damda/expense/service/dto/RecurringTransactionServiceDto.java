package io.groo.damda.expense.service.dto;

import io.groo.core.type.YNType;
import io.groo.damda.expense.domain.RecurringTransaction;
import io.groo.damda.expense.type.TxKind;
import io.groo.damda.expense.type.RecurringFrequency;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public class RecurringTransactionServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        Long toAssetRowId,
        Long fee,
        Long interestAmount,
        Long sourceExpenseRowId,
        TxKind expenseType,
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
        Boolean notifyDayBefore,
        /** 고정 수입·지출 (null = 변동). 이체는 무시된다. */
        Boolean isFixed
    ) {
        /** 고정 칸을 모르는 호출자 — 변동으로 만든다. */
        public CreateCommand(Long userRowId, Long categoryRowId, Long assetRowId, Long toAssetRowId, Long fee,
                             Long interestAmount, Long sourceExpenseRowId, TxKind expenseType, Long amount,
                             String description, String merchant, String paymentMethod,
                             RecurringFrequency frequency, Integer intervalValue, Integer dayOfWeek,
                             Integer dayOfMonth, LocalTime executionTime, LocalDate startDate, LocalDate endDate,
                             Integer maxOccurrences, Boolean autoLog, Boolean notifyDayBefore) {
            this(userRowId, categoryRowId, assetRowId, toAssetRowId, fee, interestAmount, sourceExpenseRowId,
                expenseType, amount, description, merchant, paymentMethod, frequency, intervalValue, dayOfWeek,
                dayOfMonth, executionTime, startDate, endDate, maxOccurrences, autoLog, notifyDayBefore, null);
        }
    }

    public record UpdateCommand(
        Long categoryRowId,
        Long assetRowId,
        Long toAssetRowId,
        Long fee,
        Long interestAmount,
        TxKind expenseType,
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
        Boolean notifyDayBefore,
        /** 고정 수입·지출 — null 이면 유지한다. 이체로 바뀌면 꺼진다. */
        Boolean isFixed
    ) {
        /** 고정 칸을 모르는 호출자 — 표식을 건드리지 않는다. */
        public UpdateCommand(Long categoryRowId, Long assetRowId, Long toAssetRowId, Long fee,
                             Long interestAmount, TxKind expenseType, Long amount, String description,
                             String merchant, String paymentMethod, RecurringFrequency frequency,
                             Integer intervalValue, Integer dayOfWeek, Integer dayOfMonth,
                             LocalTime executionTime, LocalDate startDate, LocalDate endDate,
                             Integer maxOccurrences, Boolean autoLog, Boolean notifyDayBefore) {
            this(categoryRowId, assetRowId, toAssetRowId, fee, interestAmount, expenseType, amount, description,
                merchant, paymentMethod, frequency, intervalValue, dayOfWeek, dayOfMonth, executionTime,
                startDate, endDate, maxOccurrences, autoLog, notifyDayBefore, null);
        }
    }

    public record RecurringInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        Long toAssetRowId,
        String toAssetName,
        Long fee,
        Long interestAmount,
        Long sourceExpenseRowId,
        TxKind expenseType,
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
        LocalDateTime modifyAt,
        /** 고정 수입·지출 여부. */
        boolean isFixed
    ) {
        public static RecurringInfo from(RecurringTransaction recurring) {
            return new RecurringInfo(
                recurring.getRowId(),
                recurring.getUser().getRowId(),
                recurring.getCategory() != null ? recurring.getCategory().getRowId() : null,
                recurring.getCategory() != null ? recurring.getCategory().getCategoryName() : null,
                recurring.getAsset() != null ? recurring.getAsset().getRowId() : null,
                recurring.getAsset() != null ? recurring.getAsset().getAssetName() : null,
                recurring.getToAsset() != null ? recurring.getToAsset().getRowId() : null,
                recurring.getToAsset() != null ? recurring.getToAsset().getAssetName() : null,
                recurring.getFee(),
                recurring.getInterestAmount(),
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
                recurring.getModifyAt(),
                recurring.isFixedTx()
            );
        }
    }
}
