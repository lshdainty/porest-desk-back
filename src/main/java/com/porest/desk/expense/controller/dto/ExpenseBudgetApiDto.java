package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseBudgetApiDto {

    public record CreateRequest(
        Long categoryRowId,
        Long budgetAmount,
        Integer budgetYear,
        Integer budgetMonth
    ) {}

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
