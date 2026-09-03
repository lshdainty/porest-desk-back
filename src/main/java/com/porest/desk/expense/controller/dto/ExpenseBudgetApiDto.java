package com.porest.desk.expense.controller.dto;

import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseBudgetApiDto {

    /*
     * 한도 상한은 거래와 같은 100억(AmountLimits.MAX_TX_AMOUNT)이다. 종전엔 상한이 없어
     * 999억 예산이 저장되고 "남은 일 권장 지출 3,703,723,671원" 같은 값이 화면에 떴다
     * (QA 2026-09-03 #48). 하한 1 은 서비스에도 있지만(0 나눗셈 방어) 여기서 400 으로
     * 먼저 거절해 API 를 직접 때리는 클라이언트도 같은 응답을 받게 한다(#47).
     */

    @Schema(name = "ExpenseBudgetCreateRequest")
    public record CreateRequest(
        Long categoryRowId,
        @Min(value = 1, message = "예산 금액은 0보다 커야 합니다")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "예산 금액은 100억원까지 입력할 수 있습니다")
        Long budgetAmount,
        Integer budgetYear,
        Integer budgetMonth
    ) {}

    @Schema(name = "ExpenseBudgetUpdateRequest")
    public record UpdateRequest(
        @Min(value = 1, message = "예산 금액은 0보다 커야 합니다")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "예산 금액은 100억원까지 입력할 수 있습니다")
        Long budgetAmount
    ) {}

    @Schema(name = "ExpenseBudgetResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        Long budgetAmount,
        Integer budgetYear,
        Integer budgetMonth,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseBudgetServiceDto.BudgetInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryRowId(),
                info.categoryName(),
                info.budgetAmount(),
                info.budgetYear(),
                info.budgetMonth(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @Schema(name = "ExpenseBudgetListResponse")
    public record ListResponse(
        List<Response> budgets
    ) {
        public static ListResponse from(List<ExpenseBudgetServiceDto.BudgetInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }

    public record ComplianceMonthResponse(
        Integer year,
        Integer month,
        Long totalLimit,
        Long totalSpent,
        Double compliancePercent
    ) {
        public static ComplianceMonthResponse from(ExpenseBudgetServiceDto.ComplianceMonth c) {
            return new ComplianceMonthResponse(
                c.year(), c.month(), c.totalLimit(), c.totalSpent(), c.compliancePercent()
            );
        }
    }

    public record ComplianceListResponse(List<ComplianceMonthResponse> months) {
        public static ComplianceListResponse from(List<ExpenseBudgetServiceDto.ComplianceMonth> months) {
            return new ComplianceListResponse(months.stream().map(ComplianceMonthResponse::from).toList());
        }
    }
}
