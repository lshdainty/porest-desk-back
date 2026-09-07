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
import jakarta.validation.constraints.NotNull;
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

    /**
     * {@code frequency} · {@code startDate} 는 <b>있어야 한다</b> — 없으면 종전엔 500 이었다
     * (QA 2026-09-07 #85). 생성·수정 모두 저장 전에 다음 실행일을 계산하는데, 그 계산이
     * {@code startDate.isBefore(오늘)} 과 {@code switch (frequency)} 로 시작해 둘 중 하나만
     * 없어도 {@code NullPointerException} 이 났다(H2 로 재현). 조회도 저장도 못 가 보고 터진다.
     *
     * <p>{@code dayOfMonth} 는 <b>1~31</b> 이다. 종전엔 32 도 200 이었는데, 스케줄러가
     * {@code min(dayOfMonth, 그 달의 마지막 날)} 로 접기 때문에 32 는 <b>31 과 똑같이</b> 동작한다 —
     * 사용자가 없는 날짜를 골랐다는 사실만 조용히 사라진다. 31 은 그대로 받는다: 같은 접기 덕에
     * 2월엔 28·29일에 도는 "말일" 이라는 뜻이 되고, 이건 매달 마지막 날에 나가는 이체·구독을
     * 적는 유일한 방법이다.
     *
     * <p>웹({@code RecurringTransactionFormValues.frequency/startDate} 필수)·앱
     * ({@code required String frequency/startDate}) 모두 항상 함께 보내고, 날짜는 달력에서
     * 고른 1~31 이다(2026-09-07 양쪽 코드로 확인).
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
        @NotNull(message = "얼마나 자주 반복할지 골라 주세요")
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        @Min(value = 1, message = "반복할 날짜는 1일부터 31일까지 고를 수 있어요")
        @Max(value = 31, message = "반복할 날짜는 1일부터 31일까지 고를 수 있어요")
        Integer dayOfMonth,
        LocalTime executionTime,
        @NotNull(message = "시작일을 골라 주세요")
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
        @NotNull(message = "얼마나 자주 반복할지 골라 주세요")
        RecurringFrequency frequency,
        Integer intervalValue,
        Integer dayOfWeek,
        @Min(value = 1, message = "반복할 날짜는 1일부터 31일까지 고를 수 있어요")
        @Max(value = 31, message = "반복할 날짜는 1일부터 31일까지 고를 수 있어요")
        Integer dayOfMonth,
        LocalTime executionTime,
        @NotNull(message = "시작일을 골라 주세요")
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
