package com.porest.desk.expense.controller.dto;

import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseBudgetApiDto {

    /*
     * 한도 상한은 거래와 같은 100억(AmountLimits.MAX_TX_AMOUNT)이다. 종전엔 상한이 없어
     * 999억 예산이 저장되고 "남은 일 권장 지출 3,703,723,671원" 같은 값이 화면에 떴다
     * (QA 2026-09-03 #48). 하한 1 은 서비스에도 있지만(0 나눗셈 방어) 여기서 400 으로
     * 먼저 거절해 API 를 직접 때리는 클라이언트도 같은 응답을 받게 한다(#47).
     */

    /**
     * 연·월은 <b>있어야 하고</b> 월은 1~12 다.
     *
     * <p>없으면 종전엔 500 이었다(QA 2026-09-07 #85). 같은 (사용자 · 카테고리 · 연월) 행을
     * 찾는 조회가 연·월로 걸러 도는데, QueryDSL 은 {@code eq(null)} 을
     * {@code IllegalArgumentException} 으로 거절하고 그것이 {@code @Repository} 프록시에서
     * {@code InvalidDataAccessApiUsageException} 으로 번역돼 매핑이 없는 채로 500 이 됐다.
     *
     * <p>월이 13 이면 <b>200 이었다</b>(QA #86) — 13월 예산 행이 생기고, 그 행은 어느 달에도
     * 안 걸려 화면에 영영 안 뜨는데 목록·이행률 집계에는 남는다. 저장 전에 끊는다.
     *
     * <p>웹({@code ExpenseBudgetFormValues})·앱({@code required int budgetYear/budgetMonth})
     * 모두 두 값을 항상 함께 보내고 값도 달력에서 고른 1~12 다(2026-09-07 양쪽 코드로 확인).
     */
    @Schema(name = "ExpenseBudgetCreateRequest")
    public record CreateRequest(
        Long categoryRowId,
        @Min(value = 1, message = "예산 금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "예산 금액은 100억원까지 입력할 수 있어요")
        Long budgetAmount,
        @NotNull(message = "예산을 세울 연도를 골라 주세요")
        Integer budgetYear,
        @NotNull(message = "예산을 세울 달을 골라 주세요")
        @Min(value = 1, message = "달은 1월부터 12월까지 고를 수 있어요")
        @Max(value = 12, message = "달은 1월부터 12월까지 고를 수 있어요")
        Integer budgetMonth
    ) {}

    @Schema(name = "ExpenseBudgetUpdateRequest")
    public record UpdateRequest(
        @Min(value = 1, message = "예산 금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "예산 금액은 100억원까지 입력할 수 있어요")
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
