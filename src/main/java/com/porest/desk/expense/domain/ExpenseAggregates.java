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
 *   <li><b>환불은 수입이 아니라 지출 상계다.</b> 지출 50,000 + 환불 3,000 이면 47,000 이다.
 *       수입으로 세면 수입도 지출도 같이 부푼다.</li>
 * </ol>
 *
 * <p>이 규칙이 서비스마다 흩어져 있어서 실제로 빠뜨린 적이 있다 — 예산 이행률 차트만 옛
 * 규칙으로 남아, 같은 화면에서 상단 카드(20361%)와 차트(20460%)가 달랐다. 그래서 한곳에 모은다.
 * <b>거래를 합산하는 코드는 여기를 거칠 것.</b>
 */
public final class ExpenseAggregates {

    private ExpenseAggregates() {}

    /** 집계 대상만 남긴다 — 기준 시각 이후(예정)는 뺀다. */
    public static List<Expense> countable(List<Expense> all, LocalDateTime now) {
        return all.stream()
            .filter(e -> e.getExpenseDate() == null || !e.getExpenseDate().isAfter(now))
            .toList();
    }

    /** 수입 합계 — 환불은 수입이 아니므로 빠진다. */
    public static long incomeSum(List<Expense> countable) {
        return countable.stream().mapToLong(Expense::incomeContribution).sum();
    }

    /** 지출 합계 — 환불이 음수로 상계된다. */
    public static long expenseSum(List<Expense> countable) {
        return countable.stream().mapToLong(Expense::expenseContribution).sum();
    }
}
