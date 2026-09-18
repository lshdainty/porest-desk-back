package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 환불 마크 — <b>삭제 대신</b>이다(설계 결정 1, 2026-09-18).
 *
 * <p>종전에는 환불이 <b>수입 행 + 원거래 연결</b>이었고 집계에서 음수로 상계했다. 그 모델은
 * 카드에서 두 번 깎였다 — 환불 수입이 카드에 {@code +금액} 흐름을 남기고, 동시에 <b>환불
 * 날짜</b> 회차의 청구에서 또 빠졌다. 두 날짜가 다른 회차면 한 번 산 것을 두 번 깎아
 * 유령 빚이 남았다.
 *
 * <p>지금은 원거래에 {@code refunded_at} 을 찍고 <b>집계에서 통째로 뺀다</b>. 그래서
 * 이 파일이 잠그는 것은 "상계가 맞나" 가 아니라 <b>"세지 않는가"</b> 다.
 */
@DisplayName("환불 마크")
class ExpenseRefundTest {

    private static final LocalDateTime BOUGHT_AT = LocalDateTime.of(2026, 7, 10, 14, 30);
    private static final LocalDateTime REFUNDED_AT = LocalDateTime.of(2026, 7, 13, 9, 0);
    /** 집계 기준 시각 — 위 거래들보다 뒤로 둔다(예정 거래 제외 규칙에 안 걸리게). */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 23, 0);

    private Expense spend(long amount) {
        return Expense.createExpense(
            null, null, null, ExpenseType.EXPENSE, amount, "구매",
            BOUGHT_AT, "무신사", "CARD", null, null, null, null);
    }

    private Expense income(long amount) {
        return Expense.createExpense(
            null, null, null, ExpenseType.INCOME, amount, "급여",
            BOUGHT_AT, "회사", "TRANSFER", null, null, null, null);
    }

    /** 집계에 들어가는 것만 남긴 뒤 합한다 — 서비스가 쓰는 규칙 그대로. */
    private long income(List<Expense> all) {
        return ExpenseAggregates.incomeSum(ExpenseAggregates.countable(all, NOW));
    }

    private long expense(List<Expense> all) {
        return ExpenseAggregates.expenseSum(ExpenseAggregates.countable(all, NOW));
    }

    @Nested
    @DisplayName("마크하면 집계에서 빠진다")
    class Marked {

        @Test
        @DisplayName("5만원 코트를 사고 환불 — 그 달 지출 0, 수입 0")
        void coatRefunded() {
            Expense coat = spend(50_000L);
            coat.markRefunded(REFUNDED_AT);

            assertThat(coat.isRefunded()).isTrue();
            assertThat(coat.isCountable()).isFalse();
            assertThat(expense(List.of(coat))).isZero();
            // 환불 달에 수입이 생기지 않는다 — 옛 모델이 여기서 18만원씩 부풀었다.
            assertThat(income(List.of(coat))).isZero();
        }

        @Test
        @DisplayName("환불한 거래의 금액·통화·가맹점은 그대로 남는다 — 내역에 보여야 한다")
        void keepsItsData() {
            Expense coat = spend(50_000L);
            coat.markRefunded(REFUNDED_AT);

            assertThat(coat.getAmount()).isEqualTo(50_000L);
            assertThat(coat.getMerchant()).isEqualTo("무신사");
            assertThat(coat.getRefundedAt()).isEqualTo(REFUNDED_AT);
        }

        /**
         * 마크 모델의 성질 — 환불은 <b>원거래 달</b>의 합계를 소급해 줄인다.
         *
         * <p>옛 모델은 환불 달에 음수를 더해 그 달 지출이 줄었다(6월에 산 것을 7월에
         * 환불하면 7월 지출이 −5만). 지금은 6월 지출이 0 이 되고 7월은 아무 일도 없다.
         */
        @Test
        @DisplayName("원거래 달이 줄고, 환불한 달에는 아무 흔적이 없다")
        void reducesOriginalMonth() {
            Expense june = spend(50_000L);
            june.markRefunded(REFUNDED_AT);

            // 6월 목록에 이 거래만 있다고 보면 — 0
            assertThat(expense(List.of(june))).isZero();
            // 7월 목록에는 이 거래가 아예 없다(행이 하나도 안 생긴다)
            assertThat(expense(List.of())).isZero();
            assertThat(income(List.of())).isZero();
        }
    }

    @Nested
    @DisplayName("환불 취소")
    class Cancelled {

        @Test
        @DisplayName("취소하면 다시 세어진다")
        void countsAgain() {
            Expense coat = spend(50_000L);
            coat.markRefunded(REFUNDED_AT);
            coat.clearRefund();

            assertThat(coat.isRefunded()).isFalse();
            assertThat(coat.isCountable()).isTrue();
            assertThat(expense(List.of(coat))).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("환급 이체 연결도 함께 지운다 — 남으면 없는 이체를 또 무르려 한다")
        void clearsTransferLink() {
            Expense coat = spend(50_000L);
            coat.markRefunded(REFUNDED_AT);
            coat.linkRefundTransfer(77L);
            assertThat(coat.getRefundTransferRowId()).isEqualTo(77L);

            coat.clearRefund();

            assertThat(coat.getRefundTransferRowId()).isNull();
        }
    }

    @Nested
    @DisplayName("수입은 수입, 지출은 지출")
    class NoMoreOffset {

        @Test
        @DisplayName("급여 300만원은 그대로 수입 — 상계 개념이 사라졌다")
        void salaryIsIncome() {
            Expense salary = income(3_000_000L);

            assertThat(salary.incomeContribution()).isEqualTo(3_000_000L);
            assertThat(salary.expenseContribution()).isZero();
        }

        @Test
        @DisplayName("수입 행은 어떤 경우에도 지출을 깎지 않는다")
        void incomeNeverOffsetsExpense() {
            List<Expense> month = List.of(income(3_000_000L), spend(250_000L));

            assertThat(income(month)).isEqualTo(3_000_000L);
            assertThat(expense(month)).isEqualTo(250_000L);
        }
    }

    @Nested
    @DisplayName("한 달 전체")
    class MonthlyScenario {

        @Test
        @DisplayName("급여 300만 · 장보기 25만 · 코트 18만 사고 환불 → 수입 300만, 지출 25만")
        void realisticMonth() {
            Expense coat = spend(180_000L);
            coat.markRefunded(REFUNDED_AT);
            List<Expense> month = List.of(income(3_000_000L), spend(250_000L), coat);

            assertThat(income(month)).isEqualTo(3_000_000L);
            assertThat(expense(month)).isEqualTo(250_000L);
            assertThat(income(month) - expense(month)).isEqualTo(2_750_000L);
        }
    }

    @Nested
    @DisplayName("삭제와의 관계")
    class VersusDelete {

        @Test
        @DisplayName("지운 거래도, 환불한 거래도 집계에서 빠진다 — 판정이 하나다")
        void bothLeaveTotals() {
            Expense deleted = spend(10_000L);
            ReflectionTestUtils.setField(deleted, "isDeleted", YNType.Y);
            Expense refunded = spend(20_000L);
            refunded.markRefunded(REFUNDED_AT);

            assertThat(deleted.isCountable()).isFalse();
            assertThat(refunded.isCountable()).isFalse();
            assertThat(expense(List.of(deleted, refunded))).isZero();
        }

        /** 아직 오지 않은 거래도 같은 판정을 지난다 — 세 조건이 한 자리에 모여 있다. */
        @Test
        @DisplayName("예정 거래도 그 자리에서 함께 걸러진다")
        void scheduledAlsoFiltered() {
            Expense future = Expense.createExpense(
                null, null, null, ExpenseType.EXPENSE, 30_000L, "다음 달 구독",
                NOW.plusDays(5), "넷플릭스", "CARD", null, null, null, null);

            assertThat(expense(List.of(future))).isZero();
        }
    }
}
