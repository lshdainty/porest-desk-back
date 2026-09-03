package com.porest.desk.expense.controller.dto;

import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseApiDto {

    /**
     * 거래 한 건의 금액 상한(100억원). DB 는 bigint 라 99조도 들어가지만 0 을 몇 개 더 찍는
     * 오타를 막을 곳이 없었다(QA 2026-09-02). 거래처·설명 길이도 컬럼(100 · 500)에 맞춰
     * 여기서 400 으로 거절한다 — 종전엔 DB 제약에 걸려 500 으로 터졌다.
     *
     * <p>값은 {@link AmountLimits#MAX_TX_AMOUNT} 하나에서 온다 — 예산·저축목표·반복거래도
     * 같은 층이라 리터럴이 갈리면 다음 사람이 틀린 쪽을 본다(QA 2026-09-03 #48 #52 #54).
     * 여기 이름을 남겨 두는 건 이미 이 상수를 참조하는 코드가 있어서다.
     */
    public static final long MAX_AMOUNT = AmountLimits.MAX_TX_AMOUNT;

    @Schema(name = "ExpenseCreateRequest")
    public record CreateRequest(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        @Max(value = MAX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long amount,
        @Size(max = 500, message = "설명은 500자까지 입력할 수 있어요")
        String description,
        // "yyyy-MM-dd" 또는 "yyyy-MM-ddTHH:mm[:ss]" 양쪽 모두 허용 — 서비스 layer 에서 유연 파싱
        String expenseDate,
        @Size(max = 100, message = "거래처는 100자까지 입력할 수 있어요")
        String merchant,
        String paymentMethod,
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). */
        Long refundOfExpenseRowId,
        /** 원 통화 금액 (해외 결제 시). null 이면 원화 결제. */
        java.math.BigDecimal originalAmount,
        /** 원 통화 (ISO 4217, 예: USD). */
        String originalCurrency,
        /** 적용 환율 (원 통화 1단위당 원화). */
        java.math.BigDecimal exchangeRate,
        Long calendarEventRowId,
        Long todoRowId
    ) {}

    @Schema(name = "ExpenseUpdateRequest")
    public record UpdateRequest(
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        @Max(value = MAX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long amount,
        @Size(max = 500, message = "설명은 500자까지 입력할 수 있어요")
        String description,
        // "yyyy-MM-dd" 또는 "yyyy-MM-ddTHH:mm[:ss]" 양쪽 모두 허용 — 서비스 layer 에서 유연 파싱
        String expenseDate,
        @Size(max = 100, message = "거래처는 100자까지 입력할 수 있어요")
        String merchant,
        String paymentMethod,
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). */
        Long refundOfExpenseRowId,
        /** 원 통화 금액 (해외 결제 시). null 이면 원화 결제. */
        java.math.BigDecimal originalAmount,
        /** 원 통화 (ISO 4217, 예: USD). */
        String originalCurrency,
        /** 적용 환율 (원 통화 1단위당 원화). */
        java.math.BigDecimal exchangeRate,
        Long calendarEventRowId,
        Long todoRowId,
        // 분할 내역 동시 수정(선택). null = 분할 미변경, 리스트 = 새 분할로 교체(금액과 합 일치 필요).
        List<ExpenseSplitApiDto.SplitRequest> splits
    ) {}

    @Schema(name = "ExpenseResponse")
    public record Response(
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
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Integer installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). */
        Long refundOfExpenseRowId,
        /** 원 통화 금액 (해외 결제 시). null 이면 원화 결제. */
        java.math.BigDecimal originalAmount,
        /** 원 통화 (ISO 4217, 예: USD). */
        String originalCurrency,
        /** 적용 환율 (원 통화 1단위당 원화). */
        java.math.BigDecimal exchangeRate,
        Long calendarEventRowId,
        Long todoRowId,
        /**
         * 시스템이 만든 거래의 출처 (TRADE_REALIZED / TRANSFER_INTEREST). null 이면 손으로 쓴 거래.
         * 값이 있으면 금액·날짜·자산이 잠긴다 — 화면이 입력을 막을 수 있게 내려 준다.
         */
        String autoSource,
        /** 이 거래에 달린 환불 건수·합계 — 지우면 함께 사라지므로 화면이 미리 알린다. */
        int refundCount,
        long refundedAmount,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        // 활성 분할 항목들의 카테고리 id (없으면 빈 리스트). 목록 카테고리 필터 split-aware 용.
        List<Long> splitCategoryRowIds
    ) {
        public static Response from(ExpenseServiceDto.ExpenseInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.categoryRowId(),
                info.categoryName(),
                info.categoryIcon(),
                info.categoryColor(),
                info.assetRowId(),
                info.assetName(),
                info.expenseType(),
                info.amount(),
                info.description(),
                info.expenseDate(),
                info.merchant(),
                info.paymentMethod(),
                info.installmentMonths(),
                info.refundOfExpenseRowId(),
                info.originalAmount(),
                info.originalCurrency(),
                info.exchangeRate(),
                info.calendarEventRowId(),
                info.todoRowId(),
                info.autoSource(),
                info.refundCount(),
                info.refundedAmount(),
                info.createAt(),
                info.modifyAt(),
                info.splitCategoryRowIds()
            );
        }
    }

    @Schema(name = "ExpenseListResponse")
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

    public record RangeSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long totalIncome,
        Long totalExpense,
        List<CategoryBreakdownResponse> categoryBreakdown,
        List<RangeMonthlyBucketResponse> monthlyBuckets
    ) {
        public static RangeSummaryResponse from(ExpenseServiceDto.RangeSummary summary) {
            List<CategoryBreakdownResponse> breakdowns = summary.categoryBreakdown().stream()
                .map(CategoryBreakdownResponse::from)
                .toList();
            List<RangeMonthlyBucketResponse> buckets = summary.monthlyBuckets().stream()
                .map(RangeMonthlyBucketResponse::from)
                .toList();
            return new RangeSummaryResponse(
                summary.startDate(),
                summary.endDate(),
                summary.totalIncome(),
                summary.totalExpense(),
                breakdowns,
                buckets
            );
        }
    }

    public record RangeMonthlyBucketResponse(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense,
        // 그 달의 카테고리별 지출(EXPENSE만, split-aware) — 카테고리 월별 추이 차트용
        List<CategoryAmountResponse> categoryExpenses
    ) {
        public static RangeMonthlyBucketResponse from(ExpenseServiceDto.RangeMonthlyBucket b) {
            return new RangeMonthlyBucketResponse(
                b.year(), b.month(), b.totalIncome(), b.totalExpense(),
                b.categoryExpenses().stream().map(CategoryAmountResponse::from).toList());
        }
    }

    public record CategoryAmountResponse(
        Long categoryRowId,
        Long amount
    ) {
        public static CategoryAmountResponse from(ExpenseServiceDto.CategoryAmount c) {
            return new CategoryAmountResponse(c.categoryRowId(), c.amount());
        }
    }

    public record MonthlyTrendResponse(
        Integer year,
        Integer month,
        Long totalIncome,
        Long totalExpense
    ) {
        public static MonthlyTrendResponse from(ExpenseServiceDto.MonthlyTrend t) {
            return new MonthlyTrendResponse(t.year(), t.month(), t.totalIncome(), t.totalExpense());
        }
    }

    public record MonthlyTrendListResponse(List<MonthlyTrendResponse> trends) {
        public static MonthlyTrendListResponse from(List<ExpenseServiceDto.MonthlyTrend> trends) {
            return new MonthlyTrendListResponse(trends.stream().map(MonthlyTrendResponse::from).toList());
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

    @Schema(name = "ExpenseAssetSummaryResponse")
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

    public record HeatmapCellResponse(
        Integer dayOfWeek,
        Integer hour,
        Long totalAmount
    ) {
        public static HeatmapCellResponse from(ExpenseServiceDto.HeatmapCell cell) {
            return new HeatmapCellResponse(cell.dayOfWeek(), cell.hour(), cell.totalAmount());
        }
    }

    public record HeatmapResponse(List<HeatmapCellResponse> cells) {
        public static HeatmapResponse from(List<ExpenseServiceDto.HeatmapCell> cells) {
            return new HeatmapResponse(cells.stream().map(HeatmapCellResponse::from).toList());
        }
    }
}
