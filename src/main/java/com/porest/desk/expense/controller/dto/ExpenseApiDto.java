package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseApiDto {

    public record CreateRequest(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId
    ) {}

    public record UpdateRequest(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId
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
        LocalDate expenseDate,
        String merchant,
        String paymentMethod,
        Long calendarEventRowId,
        Long todoRowId,
        Long groupRowId,
        String groupName,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(ExpenseServiceDto.ExpenseInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryRowId(),
                info.categoryName(),
                info.assetRowId(),
                info.assetName(),
                info.expenseType(),
                info.amount(),
                info.description(),
                info.expenseDate(),
                info.merchant(),
                info.paymentMethod(),
                info.calendarEventRowId(),
                info.todoRowId(),
                info.groupRowId(),
                info.groupName(),
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
        Long parentCategoryRowId,
        String parentCategoryName,
        ExpenseType expenseType,
        Long totalAmount
    ) {
        public static CategoryBreakdownResponse from(ExpenseServiceDto.CategoryBreakdown breakdown) {
            return new CategoryBreakdownResponse(
                breakdown.categoryRowId(),
                breakdown.categoryName(),
                breakdown.parentCategoryRowId(),
                breakdown.parentCategoryName(),
                breakdown.expenseType(),
                breakdown.totalAmount()
            );
        }
    }

    public record WeeklySummaryResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        Long totalIncome,
        Long totalExpense
    ) {
        public static WeeklySummaryResponse from(ExpenseServiceDto.WeeklySummary summary) {
            return new WeeklySummaryResponse(
                summary.weekStart(), summary.weekEnd(),
                summary.totalIncome(), summary.totalExpense()
            );
        }
    }

    public record YearlySummaryResponse(
        Integer year,
        Long totalIncome,
        Long totalExpense,
        List<MonthlyAmountResponse> monthlyAmounts
    ) {
        public static YearlySummaryResponse from(ExpenseServiceDto.YearlySummary summary) {
            return new YearlySummaryResponse(
                summary.year(), summary.totalIncome(), summary.totalExpense(),
                summary.monthlyAmounts().stream().map(MonthlyAmountResponse::from).toList()
            );
        }
    }

    public record MonthlyAmountResponse(
        Integer month,
        Long totalIncome,
        Long totalExpense,
        List<CategoryBreakdownResponse> categoryBreakdown
    ) {
        public static MonthlyAmountResponse from(ExpenseServiceDto.MonthlyAmount ma) {
            List<CategoryBreakdownResponse> breakdowns = ma.categoryBreakdown() != null
                ? ma.categoryBreakdown().stream().map(CategoryBreakdownResponse::from).toList()
                : List.of();
            return new MonthlyAmountResponse(ma.month(), ma.totalIncome(), ma.totalExpense(), breakdowns);
        }
    }

    public record MerchantSummaryResponse(String merchant, Long totalAmount, Integer count) {
        public static MerchantSummaryResponse from(ExpenseServiceDto.MerchantSummary s) {
            return new MerchantSummaryResponse(s.merchant(), s.totalAmount(), s.count());
        }
    }

    public record MerchantSummaryListResponse(List<MerchantSummaryResponse> merchants) {
        public static MerchantSummaryListResponse from(List<ExpenseServiceDto.MerchantSummary> list) {
            return new MerchantSummaryListResponse(list.stream().map(MerchantSummaryResponse::from).toList());
        }
    }

    public record AssetSummaryResponse(Long assetRowId, String assetName, Long totalAmount, Integer count) {
        public static AssetSummaryResponse from(ExpenseServiceDto.AssetSummary s) {
            return new AssetSummaryResponse(s.assetRowId(), s.assetName(), s.totalAmount(), s.count());
        }
    }

    public record AssetSummaryListResponse(List<AssetSummaryResponse> assets) {
        public static AssetSummaryListResponse from(List<ExpenseServiceDto.AssetSummary> list) {
            return new AssetSummaryListResponse(list.stream().map(AssetSummaryResponse::from).toList());
        }
    }
}
