package com.porest.desk.expense.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래 집계의 <b>단 하나의 규칙</b>.
 *
 * <p>집계는 두 가지를 지켜야 한다.
 * <ol>
 *   <li><b>아직 오지 않은 건 안 센다.</b> 반복거래는 미래분을 미리 만들어 둔다. 그걸 더하면
 *       통장에 없는 급여가 이번 달 수입으로 잡히고, 현재 시각 기준인 잔액과 어긋난다.</li>
 *   <li><b>환불된 거래는 안 센다.</b> 환불은 원거래에 찍는 표식이라(`refunded_at`)
 *       삭제와 똑같이 빠진다 — 목록엔 남지만 합계에는 없는 것으로 본다.
 *       종전엔 수입 행을 만들어 음수로 상계했는데, 그러면 환불 날짜 회차에서 또 빠져
 *       카드 청구가 두 번 깎였다(2026-09-18 마크 모델로 바꾼 이유).</li>
 * </ol>
 *
 * <p>이 규칙이 서비스마다 흩어져 있어서 실제로 빠뜨린 적이 있다 — 예산 이행률 차트만 옛
 * 규칙으로 남아, 같은 화면에서 상단 카드(20361%)와 차트(20460%)가 달랐다. 그래서 한곳에 모은다.
 * <b>거래를 합산하는 코드는 여기를 거칠 것.</b>
 */
public final class ExpenseAggregates {

    private ExpenseAggregates() {}

    /** 집계 대상만 남긴다 — 기준 시각 이후(예정)와 <b>환불된 것</b>을 뺀다. */
    public static List<Expense> countable(List<Expense> all, LocalDateTime now) {
        return notFuture(all, now).filter(Expense::isCountable).toList();
    }

    /**
     * <b>가계부</b> 집계 대상만 남긴다 — {@link #countable} 에서 카드 이월을 더 뺀다.
     *
     * <p>카드를 만들 때 적은 "이전 미결제 사용액"(D4)은 앱을 쓰기 전에 이미 쓴 돈이라
     * 등록한 달의 지출이 아니다. 지출 합계·예산·통계·홈·카테고리가 이걸 쓴다.
     *
     * <p>카드 쪽(청구·할부 회차·실적·한도 사용·카드 상세 이용 내역)은 {@link #countable}
     * 그대로다 — 그 거래가 곧 카드의 미결제 잔액이라 빼면 D4 가 풀린다.
     */
    public static List<Expense> ledgerCountable(List<Expense> all, LocalDateTime now) {
        return notFuture(all, now).filter(Expense::isLedgerCountable).toList();
    }

    private static java.util.stream.Stream<Expense> notFuture(List<Expense> all, LocalDateTime now) {
        return all.stream()
            .filter(e -> e.getExpenseDate() == null || !e.getExpenseDate().isAfter(now));
    }

    /** 수입 합계. */
    public static long incomeSum(List<Expense> countable) {
        return countable.stream().mapToLong(Expense::incomeContribution).sum();
    }

    /** 지출 합계. */
    public static long expenseSum(List<Expense> countable) {
        return countable.stream().mapToLong(Expense::expenseContribution).sum();
    }
}
