package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseApiDto {

    public record CreateRequest(
        Long categoryRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod
    ) {}

    public record UpdateRequest(
        Long categoryRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseServiceDto.ExpenseInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryRowId(),
                info.categoryName(),
                info.expenseType(),
                info.amount(),
                info.description(),
                info.expenseDate(),
                info.paymentMethod(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> expenses
    ) {
        public static ListResponse from(List<ExpenseServiceDto.ExpenseInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }

    public record DailySummaryResponse(
        LocalDate date,
        Long totalIncome,
        Long totalExpense
    ) {
        public static DailySummaryResponse from(ExpenseServiceDto.DailySummary summary) {
            return new DailySummaryResponse(
                summary.date(),
                summary.totalIncome(),
                summary.totalExpense()
            );
        }
    }

    public record MonthlySummaryResponse(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense,
        List<CategoryBreakdownResponse> categoryBreakdown
    ) {
        public static MonthlySummaryResponse from(ExpenseServiceDto.MonthlySummary summary) {
            List<CategoryBreakdownResponse> breakdowns = summary.categoryBreakdown().stream()
                .map(CategoryBreakdownResponse::from)
                .toList();
            return new MonthlySummaryResponse(
                summary.year(),
                summary.month(),
                summary.totalIncome(),
                summary.totalExpense(),
                breakdowns
            );
        }
    }

    public record CategoryBreakdownResponse(
        Long categoryRowId,
        String categoryName,
        ExpenseType expenseType,
        Long totalAmount
    ) {
        public static CategoryBreakdownResponse from(ExpenseServiceDto.CategoryBreakdown breakdown) {
            return new CategoryBreakdownResponse(
                breakdown.categoryRowId(),
                breakdown.categoryName(),
                breakdown.expenseType(),
                breakdown.totalAmount()
            );
        }
    }
}
