package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ExpenseServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). INCOME 이면서 이 값이 있으면 지출 상계로 집계. */
        Long refundOfExpenseRowId,
        Long calendarEventRowId,
        Long todoRowId
    ) {}

    public record UpdateCommand(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). INCOME 이면서 이 값이 있으면 지출 상계로 집계. */
        Long refundOfExpenseRowId,
        Long calendarEventRowId,
        Long todoRowId,
        // 분할 내역 동시 수정. null = 분할 미변경(기존 유지), 비어있지 않은 리스트 = 새 분할로 교체.
        // 금액 변경으로 기존 분할 합이 어긋날 때 클라이언트가 맞춘 분할을 함께 전달하면 원자적으로 일치화.
        List<ExpenseSplitServiceDto.SplitCommand> splits
    ) {}

    public record ExpenseInfo(
        Long rowId,
        Long userRowId,
        Long categoryRowId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        LocalDateTime expenseDate,
        String merchant,
        String paymentMethod,
        /** 할부 개월 (null = 일시불). */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). */
        Long refundOfExpenseRowId,
        Long calendarEventRowId,
        Long todoRowId,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        // 활성 분할 항목들의 카테고리 id (없으면 빈 리스트). 목록 카테고리 필터를 split-aware 하게 하기 위해 노출.
        List<Long> splitCategoryRowIds
    ) {
        public static ExpenseInfo from(Expense expense) {
            return from(expense, List.of());
        }

        public static ExpenseInfo from(Expense expense, List<Long> splitCategoryRowIds) {
            return new ExpenseInfo(
                expense.getRowId(),
                expense.getUser().getRowId(),
                // category 는 nullable(미분류 거래·카테고리 정리 등) — null-safe 매핑
                expense.getCategory() != null ? expense.getCategory().getRowId() : null,
                expense.getCategory() != null ? expense.getCategory().getCategoryName() : null,
                expense.getCategory() != null ? expense.getCategory().getIcon() : null,
                expense.getCategory() != null ? expense.getCategory().getColor() : null,
                expense.getAsset() != null ? expense.getAsset().getRowId() : null,
                expense.getAsset() != null ? expense.getAsset().getAssetName() : null,
                expense.getExpenseType(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getMerchant(),
                expense.getPaymentMethod(),
                expense.getInstallmentMonths(),
                expense.getRefundOfExpenseRowId(),
                expense.getCalendarEvent() != null ? expense.getCalendarEvent().getRowId() : null,
                expense.getTodo() != null ? expense.getTodo().getRowId() : null,
                expense.getCreateAt(),
                expense.getModifyAt(),
                splitCategoryRowIds != null ? splitCategoryRowIds : List.of()
            );
        }
    }

    public record DailySummary(
        LocalDate date,
        Long totalIncome,
        Long totalExpense
    ) {}

    /// 임의 기간 요약. 도넛/하이라이트 + 추이 차트용 monthlyBuckets 포함.
    public record RangeSummary(
        LocalDate startDate,
        LocalDate endDate,
        Long totalIncome,
        Long totalExpense,
        List<CategoryBreakdown> categoryBreakdown,
        List<RangeMonthlyBucket> monthlyBuckets
    ) {}

    public record RangeMonthlyBucket(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense,
        // 그 달의 카테고리별 지출(EXPENSE만, split-aware). 카테고리 월별 추이(TOP N stacked) 차트용.
        List<CategoryAmount> categoryExpenses
    ) {}

    /// 카테고리 단위 금액(월별 지출 분해 등). categoryRowId = leaf(분할 시 분할) 카테고리.
    public record CategoryAmount(
        Long categoryRowId,
        Long amount
    ) {}

    public record MonthlyTrend(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense
    ) {}

    public record CategoryBreakdown(
        Long categoryRowId,
        String categoryName,
        Long parentCategoryRowId,
        String parentCategoryName,
        ExpenseType expenseType,
        Long totalAmount
    ) {}

    public record MerchantSummary(
        String merchant,
        Long totalAmount,
        Integer count
    ) {}

    public record AssetSummary(
        Long assetRowId,
        String assetName,
        Long totalAmount,
        Integer count
    ) {}

    public record HeatmapCell(
        Integer dayOfWeek,
        Integer hour,
        Long totalAmount
    ) {}

    public record SearchCommand(
        Long userRowId,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        String keyword,
        String merchant,
        Long minAmount,
        Long maxAmount,
        LocalDate startDate,
        LocalDate endDate
    ) {}
}
