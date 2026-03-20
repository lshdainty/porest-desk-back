package com.porest.desk.expense.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class RecurringTransactionApiDto {

    public record CreateRequest(
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
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record Response(
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
        public static Response from(RecurringTransactionServiceDto.RecurringInfo info) {
            return new Response(
                info.rowId(), info.userRowId(),
                info.categoryRowId(), info.categoryName(),
                info.assetRowId(), info.assetName(),
                info.expenseType(), info.amount(), info.description(),
                info.merchant(), info.paymentMethod(),
                info.frequency(), info.intervalValue(),
                info.dayOfWeek(), info.dayOfMonth(),
                info.startDate(), info.endDate(),
                info.nextExecutionDate(), info.lastExecutedAt(),
                info.isActive(), info.createAt(), info.modifyAt()
            );
        }
    }

    public record ListResponse(List<Response> recurringTransactions) {
        public static ListResponse from(List<RecurringTransactionServiceDto.RecurringInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
