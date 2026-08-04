package com.porest.desk.expense.domain;

import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 환불 상계 — 실제로 물건을 사고 무르는 상황을 그대로 넣어 검증한다.
 *
 * <p>핵심 규칙: 환불은 <b>수입이 아니다</b>. 5만원 옷을 사고 환불하면 그 달 지출도 수입도 0 이어야지,
 * 지출 5만 + 수입 5만이 되면 안 된다(수입이 부풀려지고 저축률·수지 통계가 전부 틀어진다).
 */
@DisplayName("환불 상계")
class ExpenseRefundTest {

    private static final LocalDateTime BOUGHT_AT = LocalDateTime.of(2026, 7, 10, 14, 30);

    /** 카드 지출. */
    private Expense spend(long amount) {
        return Expense.createExpense(
            null, null, null, ExpenseType.EXPENSE, amount, "구매",
            BOUGHT_AT, "무신사", "CARD", null, null,
            null,
            null,
            null);
    }

    /** 환불 — INCOME 이면서 원거래를 가리킨다. */
    private Expense refund(long amount, long ofExpenseRowId) {
        return Expense.createExpense(
            null, null, null, ExpenseType.INCOME, amount, "환불",
            BOUGHT_AT.plusDays(3), "무신사", "CARD", null, ofExpenseRowId,
            null,
            null,
            null);
    }

    /** 순수 수입 — 원거래가 없다. */
    private Expense income(long amount) {
        return Expense.createExpense(
            null, null, null, ExpenseType.INCOME, amount, "급여",
            BOUGHT_AT, "회사", "TRANSFER", null, null,
            null,
            null,
            null);
    }

    private long sumIncome(List<Expense> list) {
        return list.stream().mapToLong(Expense::incomeContribution).sum();
    }

    private long sumExpense(List<Expense> list) {
        return list.stream().mapToLong(Expense::expenseContribution).sum();
    }

    @Nested
    @DisplayName("전액 환불")
    class FullRefund {

        @Test
        @DisplayName("5만원 코트를 사고 전액 환불 — 그 달 지출 0, 수입 0")
        void coatFullyRefunded() {
            Expense bought = spend(50_000L);
            Expense refunded = refund(50_000L, 100L);

            assertThat(refunded.isRefund()).isTrue();
            assertThat(sumExpense(List.of(bought, refunded))).isZero();
            assertThat(sumIncome(List.of(bought, refunded))).isZero();
        }

        @Test
        @DisplayName("환불만 있고 원거래가 이 기간 밖이면 지출이 음수로 남는다 — 실제 현금 흐름과 같다")
        void refundOnlyInPeriod() {
            // 6월에 산 물건을 7월에 환불 — 7월만 보면 돈이 들어온 것이라 지출이 −5만
            Expense refunded = refund(50_000L, 100L);

            assertThat(sumExpense(List.of(refunded))).isEqualTo(-50_000L);
            assertThat(sumIncome(List.of(refunded))).isZero();
        }
    }

    @Nested
    @DisplayName("부분 환불")
    class PartialRefund {

        @Test
        @DisplayName("12만원 운동화 중 사이즈 교환 차액 3만원 환불 — 지출 9만원만 남는다")
        void sneakersPartialRefund() {
            Expense bought = spend(120_000L);
            Expense refunded = refund(30_000L, 100L);

            assertThat(sumExpense(List.of(bought, refunded))).isEqualTo(90_000L);
            assertThat(sumIncome(List.of(bought, refunded))).isZero();
        }

        @Test
        @DisplayName("여러 번 나눠 환불받아도 합계가 맞는다 (8만원 중 3만 + 2만)")
        void multipleRefunds() {
            Expense bought = spend(80_000L);
            List<Expense> all = List.of(bought, refund(30_000L, 100L), refund(20_000L, 100L));

            assertThat(sumExpense(all)).isEqualTo(30_000L);
            assertThat(sumIncome(all)).isZero();
        }
    }

    @Nested
    @DisplayName("환불이 아닌 수입")
    class NotRefund {

        @Test
        @DisplayName("급여 300만원은 원거래가 없어 그대로 수입 — 상계 대상이 아니다")
        void salaryIsIncome() {
            Expense salary = income(3_000_000L);

            assertThat(salary.isRefund()).isFalse();
            assertThat(sumIncome(List.of(salary))).isEqualTo(3_000_000L);
            assertThat(sumExpense(List.of(salary))).isZero();
        }

        @Test
        @DisplayName("적금 이자 12,340원도 수입 — 예·적금 계좌로 들어오는 돈은 환불이 아니다")
        void interestIsIncome() {
            assertThat(sumIncome(List.of(income(12_340L)))).isEqualTo(12_340L);
        }

        @Test
        @DisplayName("원거래를 가리켜도 EXPENSE 면 환불이 아니다 — 타입이 기준")
        void expenseWithRefundLinkIsNotRefund() {
            Expense e = Expense.createExpense(
                null, null, null, ExpenseType.EXPENSE, 10_000L, "이상한 데이터",
                BOUGHT_AT, "가맹점", "CARD", null, 100L,
            null,
            null,
            null);

            assertThat(e.isRefund()).isFalse();
            assertThat(sumExpense(List.of(e))).isEqualTo(10_000L);
        }
    }

    @Nested
    @DisplayName("한 달 전체 시나리오")
    class MonthlyScenario {

        @Test
        @DisplayName("급여 300만 · 장보기 25만 · 코트 18만 구매 후 환불 → 수입 300만, 지출 25만")
        void realisticMonth() {
            List<Expense> month = List.of(
                income(3_000_000L),        // 급여
                spend(250_000L),           // 장보기
                spend(180_000L),           // 코트
                refund(180_000L, 100L)     // 코트 환불
            );

            assertThat(sumIncome(month)).isEqualTo(3_000_000L);
            assertThat(sumExpense(month)).isEqualTo(250_000L);
            // 수지 = 수입 − 지출
            assertThat(sumIncome(month) - sumExpense(month)).isEqualTo(2_750_000L);
        }

        @Test
        @DisplayName("환불을 수입으로 잡던 옛 방식과의 차이 — 수입이 18만원 부풀지 않는다")
        void doesNotInflateIncome() {
            List<Expense> month = List.of(spend(180_000L), refund(180_000L, 100L));

            // 옛 방식이면 수입 180,000 / 지출 180,000 으로 잡혔다
            assertThat(sumIncome(month)).isZero();
            assertThat(sumExpense(month)).isZero();
        }
    }

    @Test
    @DisplayName("수정으로 원거래 연결을 지우면 다시 일반 수입이 된다")
    void unlinkMakesItIncomeAgain() {
        Expense e = refund(50_000L, 100L);
        assertThat(e.isRefund()).isTrue();

        e.updateExpense(null, null, ExpenseType.INCOME, 50_000L, "잘못 연결한 것 해제",
            BOUGHT_AT, "무신사", "CARD", null, null,
            null,
            null,
            null);

        assertThat(e.isRefund()).isFalse();
        assertThat(e.incomeContribution()).isEqualTo(50_000L);
    }
}
