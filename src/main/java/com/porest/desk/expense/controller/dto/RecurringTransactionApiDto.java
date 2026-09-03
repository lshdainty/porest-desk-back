package com.porest.desk.expense.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

public class RecurringTransactionApiDto {

    /*
     * 반복 거래가 만드는 건 결국 거래다 — 상한이 거래(100억)보다 크면 거래 상한을 우회하는
     * 경로가 된다. 종전엔 상한이 없어 99조짜리 반복 설정이 만들어지고 스케줄러가 그 금액으로
     * 거래를 찍었다(QA 2026-09-03 #54). 길이는 컬럼 폭(description 500 · merchant 100)이다.
     */

    @Schema(name = "RecurringTransactionCreateRequest")
    public record CreateRequest(
        Long categoryRowId,
        Long assetRowId,
        Long sourceExpenseRowId,
        ExpenseType expenseType,
        @Min(value = 1, message = "금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long amount,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "설명은 500자까지 입력할 수 있어요")
        String description,
        @Size(max = 100, message = "거래처는 100자까지 입력할 수 있어요")
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
        @Min(value = 1, message = "금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long amount,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "설명은 500자까지 입력할 수 있어요")
        String description,
        @Size(max = 100, message = "거래처는 100자까지 입력할 수 있어요")
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
