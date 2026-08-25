package com.porest.desk.expense.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

public class RecurringTransactionApiDto {

    @Schema(name = "RecurringTransactionCreateRequest")
    public record CreateRequest(
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

    @Schema(name = "RecurringTransactionUpdateRequest")
    public record UpdateRequest(
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

    @Schema(name = "RecurringTransactionResponse")
    public record Response(
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
        public static Response from(RecurringTransactionServiceDto.RecurringInfo info) {
            return new Response(
                info.rowId(), info.userRowId(),
                info.categoryRowId(), info.categoryName(),
                info.assetRowId(), info.assetName(),
                info.sourceExpenseRowId(),
                info.expenseType(), info.amount(), info.description(),
                info.merchant(), info.paymentMethod(),
                info.frequency(), info.intervalValue(),
                info.dayOfWeek(), info.dayOfMonth(),
                info.executionTime(),
                info.startDate(), info.endDate(),
                info.maxOccurrences(), info.executedCount(),
                info.nextExecutionDate(), info.lastExecutedAt(),
                info.isActive(),
                info.autoLog(), info.notifyDayBefore(),
                info.createAt(), info.modifyAt()
            );
        }
    }

    @Schema(name = "RecurringTransactionListResponse")
    public record ListResponse(List<Response> recurringTransactions) {
        public static ListResponse from(List<RecurringTransactionServiceDto.RecurringInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
