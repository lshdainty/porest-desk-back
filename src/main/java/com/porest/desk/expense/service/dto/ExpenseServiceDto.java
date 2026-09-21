package com.porest.desk.expense.service.dto;

import com.porest.desk.common.patch.Patch;
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
        /** 원 통화 금액 (해외 결제 시). null 이면 원화 결제. */
        java.math.BigDecimal originalAmount,
        /** 원 통화 (ISO 4217, 예: USD). */
        String originalCurrency,
        /** 적용 환율 (원 통화 1단위당 원화). */
        java.math.BigDecimal exchangeRate,
        Long calendarEventRowId,
        Long todoRowId
    ) {}

    /**
     * 수정 명령 — 각 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}).
     * {@code splits} 만 종전 그대로다({@code null}=미변경, 리스트=교체).
     */
    public record UpdateCommand(
        Patch<Long> categoryRowId,
        Patch<Long> assetRowId,
        Patch<ExpenseType> expenseType,
        Patch<Long> amount,
        Patch<String> description,
        Patch<LocalDateTime> expenseDate,
        Patch<String> merchant,
        Patch<String> paymentMethod,
        /** 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미. */
        Patch<Integer> installmentMonths,
        /** 환불 원거래 행 아이디 (null = 환불 아님). INCOME 이면서 이 값이 있으면 지출 상계로 집계. */
        /** 원 통화 금액 (해외 결제 시). null 이면 원화 결제. */
        Patch<java.math.BigDecimal> originalAmount,
        /** 원 통화 (ISO 4217, 예: USD). */
        Patch<String> originalCurrency,
        /** 적용 환율 (원 통화 1단위당 원화). */
        Patch<java.math.BigDecimal> exchangeRate,
        Patch<Long> calendarEventRowId,
        Patch<Long> todoRowId,
        // 분할 내역 동시 수정. null = 분할 미변경(기존 유지), 비어있지 않은 리스트 = 새 분할로 교체.
        // 금액 변경으로 기존 분할 합이 어긋날 때 클라이언트가 맞춘 분할을 함께 전달하면 원자적으로 일치화.
        List<ExpenseSplitServiceDto.SplitCommand> splits,
        /**
         * 거래 일시가 날짜만(yyyy-MM-dd) 왔는가 — 잠긴 거래(D12)의 "돈 칸이 달라졌나" 를 날짜로만 본다.
         * 시각이 없는 형식을 00:00 으로 읽으므로, 이게 없으면 저장된 시각과 달라 보여 오탐한다.
         */
        boolean expenseDateDateOnly
    ) {
        /** 날짜 형식을 모르는 호출자(가져오기·테스트) — 시각까지 온 것으로 본다. */
        public UpdateCommand(Patch<Long> categoryRowId, Patch<Long> assetRowId, Patch<ExpenseType> expenseType,
                             Patch<Long> amount, Patch<String> description, Patch<LocalDateTime> expenseDate,
                             Patch<String> merchant, Patch<String> paymentMethod, Patch<Integer> installmentMonths,
                             Patch<java.math.BigDecimal> originalAmount, Patch<String> originalCurrency,
                             Patch<java.math.BigDecimal> exchangeRate, Patch<Long> calendarEventRowId,
                             Patch<Long> todoRowId, List<ExpenseSplitServiceDto.SplitCommand> splits) {
            this(categoryRowId, assetRowId, expenseType, amount, description, expenseDate, merchant, paymentMethod,
                installmentMonths, originalAmount, originalCurrency, exchangeRate, calendarEventRowId, todoRowId,
                splits, false);
        }
    }

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
        /**
         * 환불 처리 시각 (null = 환불 아님). 있으면 화면이 취소선·"환불됨" 배지로 그리고
         * 합계에서 빠진 상태다.
         */
        LocalDateTime refundedAt,
        /** 환불 마크가 만든 카드→결제계좌 환급 이체 (null = 없음). */
        Long refundTransferRowId,
        /** 원 통화 금액 (해외 결제 시). */
        java.math.BigDecimal originalAmount,
        /** 원 통화 (ISO 4217). */
        String originalCurrency,
        /** 적용 환율. */
        java.math.BigDecimal exchangeRate,
        Long calendarEventRowId,
        Long todoRowId,
        /**
         * 시스템이 만든 거래의 출처 (TRADE_REALIZED / TRANSFER_INTEREST). null 이면 손으로 쓴 거래.
         * 값이 있으면 금액·날짜·자산이 잠긴다 — 화면이 입력을 막을 수 있게 내려 준다.
         */
        String autoSource,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        // 활성 분할 항목들의 카테고리 id (없으면 빈 리스트). 목록 카테고리 필터를 split-aware 하게 하기 위해 노출.
        List<Long> splitCategoryRowIds,
        /**
         * 이 요청이 <b>방금 만든</b> 카드 환급액 (없으면 null) — 거래의 속성이 아니라
         * 그 저장의 결과다(설계 13-1).
         *
         * <p>결제 완료 회차의 카드 거래를 줄이면 그만큼 결제계좌로 돌아간다. 화면이
         * "결제계좌로 N원이 환급됐어요" 를 말할 재료라, 조회에는 늘 null 이다.
         */
        Long refundedAmount,
        /**
         * 이 날짜(회차 말일)까지의 카드 회차분은 계좌 이체 없이 정리된 기록용 (null = 정상).
         * 닫힌 회차에 소급 입력한 카드 지출에 붙는다(닫힌 회차 규칙 R2·R5).
         */
        LocalDate cardSettledThrough,
        /** 그 가운데 기록만 남긴 금액 — 할부는 지난 회차분만이라 거래 금액보다 작을 수 있다 (null = 없음). */
        Long recordOnlyAmount,
        /**
         * 돈 칸 잠금(D12) — 결제일이 된 회차분이 하나라도 있는(또는 기록용 표식이 있는) 신용카드 거래.
         * 금액·날짜·시간·자산·할부·유형·통화 3칸·결제수단을 못 고친다. 고치려면 고쳐 쓰기.
         */
        boolean moneyLocked
    ) {
        /** 잠금만 바꾼 사본 — 잠금은 카드의 회차 결제일을 봐야 해서 서비스가 따로 정한다. */
        public ExpenseInfo withMoneyLocked(boolean locked) {
            return new ExpenseInfo(rowId, userRowId, categoryRowId, categoryName, categoryIcon, categoryColor,
                assetRowId, assetName, expenseType, amount, description, expenseDate, merchant, paymentMethod,
                installmentMonths, refundedAt, refundTransferRowId, originalAmount, originalCurrency,
                exchangeRate, calendarEventRowId, todoRowId, autoSource, createAt, modifyAt,
                splitCategoryRowIds, refundedAmount, cardSettledThrough, recordOnlyAmount, locked);
        }

        public static ExpenseInfo from(Expense expense) {
            return from(expense, List.of());
        }

        public static ExpenseInfo from(Expense expense, List<Long> splitCategoryRowIds) {
            return from(expense, splitCategoryRowIds, null);
        }

        public static ExpenseInfo from(Expense expense, List<Long> splitCategoryRowIds,
                                       Long refundedAmount) {
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
                expense.getRefundedAt(),
                expense.getRefundTransferRowId(),
                expense.getOriginalAmount(),
                expense.getOriginalCurrency(),
                expense.getExchangeRate(),
                expense.getCalendarEvent() != null ? expense.getCalendarEvent().getRowId() : null,
                expense.getTodo() != null ? expense.getTodo().getRowId() : null,
                expense.getAutoSource(),
                expense.getCreateAt(),
                expense.getModifyAt(),
                splitCategoryRowIds != null ? splitCategoryRowIds : List.of(),
                refundedAmount,
                expense.getCardSettledThrough(),
                recordOnlyAmountOf(expense),
                false
            );
        }

        private static Long recordOnlyAmountOf(Expense expense) {
            if (expense.getCardSettledThrough() == null) {
                return null;
            }
            long sum = Math.abs(com.porest.desk.card.service.CardCycleMath.duesByCycle(expense).entrySet().stream()
                .filter(e -> com.porest.desk.card.service.CardCycleMath.isRecordOnly(
                    e.getKey(), expense.getCardSettledThrough()))
                .mapToLong(Map.Entry::getValue)
                .sum());
            return sum > 0L ? sum : null;
        }
    }

    /**
     * 카드 정산 미리보기 — 저장·삭제·수정·환불 확인창이 돈의 움직임을 예고할 재료
     * (설계 13-1, 닫힌 회차 R2·R3·R6).
     *
     * <p>{@code applies=false} 면 이 변경으로 돌려줄 돈이 없다. {@code reason} 으로 화면이
     * 문구를 고른다 — 금액 줄 · "이미 환급된 거래" 줄 · "결제한 달이 지나 기록만 정리돼요" 줄 · 줄 없음.
     *
     * @param newRecordAmount      이번 저장으로 기록만 남는 금액 — 닫힌 회차에 떨어진 몫(R2)
     * @param sameDayExtraPayment  오늘이 결제일이라 결제계좌에서 추가로 빠질 금액(R3)
     */
    public record RefundPreviewInfo(boolean applies, long refundAmount, String reason,
                                    long newRecordAmount, long sameDayExtraPayment) {
        public static RefundPreviewInfo none(String reason) {
            return new RefundPreviewInfo(false, 0L, reason, 0L, 0L);
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
