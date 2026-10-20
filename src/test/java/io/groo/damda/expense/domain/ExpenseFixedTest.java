package io.groo.damda.expense.domain;

import io.groo.damda.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정 수입·지출 표식 — 집계의 <b>분류</b>다. 돈의 크기는 그대로고, 어느 몫이 고정인지만 나눈다.
 *
 * <p>고정 합계는 전체 합계의 <b>부분집합</b>이어야 한다. 그래야 "변동 = 전체 − 고정" 이 성립해
 * 통계 화면의 세 숫자(전체·고정·변동)가 서로 어긋나지 않는다.
 */
@DisplayName("고정 수입·지출 표식")
class ExpenseFixedTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 7, 10, 9, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 23, 0);

    private Expense tx(ExpenseType type, long amount, boolean fixed) {
        Expense e = Expense.createExpense(null, null, null, type, amount, null, AT, null, null, null,
            null, null, null);
        e.markFixed(fixed);
        return e;
    }

    @Test
    @DisplayName("새 거래는 변동이다 — 표식을 안 달면 기존 동작 그대로")
    void defaultsToVariable() {
        Expense e = Expense.createExpense(null, null, null, ExpenseType.EXPENSE, 1_000L, null, AT, null, null,
            null, null, null, null);

        assertThat(e.isFixedTx()).isFalse();
        assertThat(e.fixedExpenseContribution()).isZero();
    }

    @Test
    @DisplayName("markFixed(null) 은 변동이다")
    void nullMeansVariable() {
        Expense e = tx(ExpenseType.EXPENSE, 1_000L, true);
        e.markFixed(null);

        assertThat(e.isFixedTx()).isFalse();
    }

    @Test
    @DisplayName("고정 합계는 수입·지출 각각 자기 유형만 센다")
    void fixedSumsAreSeparatedByType() {
        List<Expense> all = List.of(
            tx(ExpenseType.INCOME, 3_000_000L, true),   // 월급 (고정)
            tx(ExpenseType.INCOME, 200_000L, false),    // 부수입 (변동)
            tx(ExpenseType.EXPENSE, 700_000L, true),    // 월세 (고정)
            tx(ExpenseType.EXPENSE, 50_000L, false));   // 점심 (변동)
        List<Expense> countable = ExpenseAggregates.countable(all, NOW);

        assertThat(ExpenseAggregates.incomeSum(countable)).isEqualTo(3_200_000L);
        assertThat(ExpenseAggregates.fixedIncomeSum(countable)).isEqualTo(3_000_000L);
        assertThat(ExpenseAggregates.expenseSum(countable)).isEqualTo(750_000L);
        assertThat(ExpenseAggregates.fixedExpenseSum(countable)).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("환불된 고정 거래는 전체에서도 고정에서도 빠진다 — 부분집합 관계가 깨지지 않는다")
    void refundedFixedLeavesBothTotals() {
        Expense rent = tx(ExpenseType.EXPENSE, 700_000L, true);
        rent.markRefunded(AT.plusDays(1));
        List<Expense> countable = ExpenseAggregates.countable(
            List.of(rent, tx(ExpenseType.EXPENSE, 50_000L, false)), NOW);

        assertThat(ExpenseAggregates.expenseSum(countable)).isEqualTo(50_000L);
        assertThat(ExpenseAggregates.fixedExpenseSum(countable)).isZero();
    }

    @Test
    @DisplayName("아직 오지 않은 고정 거래(예정)는 세지 않는다 — 반복 거래가 미리 만들어 둔 월세")
    void futureFixedIsNotCounted() {
        Expense upcoming = tx(ExpenseType.EXPENSE, 700_000L, true);
        org.springframework.test.util.ReflectionTestUtils.setField(upcoming, "expenseDate", NOW.plusDays(5));
        List<Expense> countable = ExpenseAggregates.countable(List.of(upcoming), NOW);

        assertThat(ExpenseAggregates.fixedExpenseSum(countable)).isZero();
        assertThat(ExpenseAggregates.expenseSum(countable)).isZero();
    }
}
