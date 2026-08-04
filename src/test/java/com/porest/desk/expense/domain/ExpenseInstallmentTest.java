package com.porest.desk.expense.domain;

import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 할부 회차 금액 — 실제로 카드로 긁는 상황을 그대로 넣어 검증한다.
 *
 * <p>핵심 규칙: 회차 합은 언제나 원금과 정확히 같아야 한다. 나누어떨어지지 않는 금액은
 * 첫 회차에 나머지를 몰아 잔돈이 사라지거나 더 생기지 않게 한다(국내 카드사 관행).
 */
@DisplayName("할부 회차 금액 계산")
class ExpenseInstallmentTest {

    private Expense card(long amount, Integer installmentMonths) {
        return Expense.createExpense(
            null, null, null,
            ExpenseType.EXPENSE, amount, "테스트",
            LocalDateTime.of(2026, 7, 10, 14, 30),
            "테스트가맹점", "CARD", installmentMonths);
    }

    /** 1..n 회차 합 — 원금과 일치해야 한다. */
    private long totalOf(Expense e, int months) {
        return IntStream.rangeClosed(1, months).mapToLong(e::installmentAmountAt).sum();
    }

    @Nested
    @DisplayName("딱 나누어떨어지는 금액")
    class EvenSplit {

        @Test
        @DisplayName("아이폰 150만원 6개월 할부 — 매달 25만원씩")
        void iphone6Months() {
            Expense e = card(1_500_000L, 6);

            assertThat(e.isInstallment()).isTrue();
            assertThat(e.installmentAmountAt(1)).isEqualTo(250_000L);
            assertThat(e.installmentAmountAt(3)).isEqualTo(250_000L);
            assertThat(e.installmentAmountAt(6)).isEqualTo(250_000L);
            assertThat(totalOf(e, 6)).isEqualTo(1_500_000L);
        }

        @Test
        @DisplayName("냉장고 240만원 12개월 무이자 — 매달 20만원씩")
        void refrigerator12Months() {
            Expense e = card(2_400_000L, 12);

            assertThat(e.installmentAmountAt(1)).isEqualTo(200_000L);
            assertThat(e.installmentAmountAt(12)).isEqualTo(200_000L);
            assertThat(totalOf(e, 12)).isEqualTo(2_400_000L);
        }
    }

    @Nested
    @DisplayName("나누어떨어지지 않는 금액 — 첫 회차에 나머지")
    class UnevenSplit {

        @Test
        @DisplayName("노트북 100만원 3개월 — 333,334 / 333,333 / 333,333 (합 100만원)")
        void laptop3Months() {
            Expense e = card(1_000_000L, 3);

            assertThat(e.installmentAmountAt(1)).isEqualTo(333_334L); // 나머지 1원
            assertThat(e.installmentAmountAt(2)).isEqualTo(333_333L);
            assertThat(e.installmentAmountAt(3)).isEqualTo(333_333L);
            assertThat(totalOf(e, 3)).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("타이어 교체 47만원 5개월 — 94,000원씩 딱 떨어져 나머지 없음")
        void tire5Months() {
            Expense e = card(470_000L, 5);

            assertThat(e.installmentAmountAt(1)).isEqualTo(94_000L);
            assertThat(totalOf(e, 5)).isEqualTo(470_000L);
        }

        @Test
        @DisplayName("치과 임플란트 130만원 7개월 — 나머지 4원이 첫 회차로")
        void implant7Months() {
            Expense e = card(1_300_000L, 7);

            // 1,300,000 / 7 = 185,714 …나머지 2
            assertThat(e.installmentAmountAt(1)).isEqualTo(185_716L);
            assertThat(e.installmentAmountAt(2)).isEqualTo(185_714L);
            assertThat(totalOf(e, 7)).isEqualTo(1_300_000L);
        }
    }

    @Nested
    @DisplayName("일시불 취급")
    class LumpSum {

        @Test
        @DisplayName("스타벅스 5,500원 일시불(null) — 1회차에 전액, 그 뒤는 0")
        void coffeeNull() {
            Expense e = card(5_500L, null);

            assertThat(e.isInstallment()).isFalse();
            assertThat(e.installmentAmountAt(1)).isEqualTo(5_500L);
            assertThat(e.installmentAmountAt(2)).isZero();
        }

        @Test
        @DisplayName("1개월 할부는 일시불과 같다 — null 로 정규화")
        void oneMonthIsLumpSum() {
            Expense e = card(89_000L, 1);

            assertThat(e.getInstallmentMonths()).isNull();
            assertThat(e.isInstallment()).isFalse();
            assertThat(e.installmentAmountAt(1)).isEqualTo(89_000L);
        }

        @Test
        @DisplayName("0개월·음수는 잘못된 입력 — 일시불로 정규화")
        void invalidMonthsNormalized() {
            assertThat(card(10_000L, 0).getInstallmentMonths()).isNull();
            assertThat(card(10_000L, -3).getInstallmentMonths()).isNull();
        }
    }

    @Nested
    @DisplayName("회차 범위 밖")
    class OutOfRange {

        @Test
        @DisplayName("3개월 할부의 4회차는 0원 — 할부가 끝나면 더 청구되지 않는다")
        void afterLastSeq() {
            Expense e = card(600_000L, 3);

            assertThat(e.installmentAmountAt(3)).isEqualTo(200_000L);
            assertThat(e.installmentAmountAt(4)).isZero();
            assertThat(e.installmentAmountAt(99)).isZero();
        }

        @Test
        @DisplayName("결제 전 기간(0회차 이하)은 0원 — 사기 전에는 청구되지 않는다")
        void beforeFirstSeq() {
            Expense e = card(600_000L, 3);

            assertThat(e.installmentAmountAt(0)).isZero();
            assertThat(e.installmentAmountAt(-1)).isZero();
        }
    }

    @Test
    @DisplayName("수정으로 할부 개월이 바뀌면 회차 금액도 따라 바뀐다 — 3개월 → 6개월 변경")
    void updateChangesInstallment() {
        Expense e = card(1_200_000L, 3);
        assertThat(e.installmentAmountAt(1)).isEqualTo(400_000L);

        e.updateExpense(null, null, ExpenseType.EXPENSE, 1_200_000L, "수정",
            LocalDateTime.of(2026, 7, 10, 14, 30), "테스트가맹점", "CARD", 6);

        assertThat(e.installmentAmountAt(1)).isEqualTo(200_000L);
        assertThat(totalOf(e, 6)).isEqualTo(1_200_000L);
    }
}
