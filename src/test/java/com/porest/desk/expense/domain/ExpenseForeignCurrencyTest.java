package com.porest.desk.expense.domain;

import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 해외 결제의 원 통화 기록 — 실제 카드 명세서를 그대로 넣어 검증한다.
 *
 * <p>원화 환산액(amount)만 남기면 "얼마짜리를 어떤 환율로 샀는지" 가 사라져 카드사 청구 환율과
 * 대사할 수 없다. 잔액·통계는 종전대로 amount 를 쓰고, 원 통화 3종은 기록으로만 남는다.
 */
@DisplayName("해외 결제 원 통화 기록")
class ExpenseForeignCurrencyTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 7, 12, 21, 40);

    private Expense pay(long krw, BigDecimal originalAmount, String currency, BigDecimal rate) {
        return Expense.createExpense(
            null, null, null, ExpenseType.EXPENSE, krw,
            "해외 결제", PAID_AT, null, "CREDIT_CARD", null, null,
            originalAmount, currency, rate);
    }

    @Nested
    @DisplayName("해외 카드 결제")
    class ForeignPayment {

        @Test
        @DisplayName("샌프란시스코 스타벅스 $5.50, 환율 1,400 — 7,700원과 원 통화가 함께 남는다")
        void starbucksUsd() {
            Expense e = pay(7_700L, new BigDecimal("5.50"), "USD", new BigDecimal("1400"));

            assertThat(e.isForeignCurrency()).isTrue();
            assertThat(e.getAmount()).isEqualTo(7_700L);              // 잔액·통계는 원화
            assertThat(e.getOriginalAmount()).isEqualByComparingTo("5.50");
            assertThat(e.getOriginalCurrency()).isEqualTo("USD");
            assertThat(e.getExchangeRate()).isEqualByComparingTo("1400");
        }

        @Test
        @DisplayName("$5.50 × 1,400 = 7,700 — 명세서 환산액과 대사된다")
        void reconcilesWithStatement() {
            Expense e = pay(7_700L, new BigDecimal("5.50"), "USD", new BigDecimal("1400"));

            long computed = e.getOriginalAmount().multiply(e.getExchangeRate())
                .setScale(0, RoundingMode.HALF_UP).longValueExact();

            assertThat(computed).isEqualTo(e.getAmount());
        }

        @Test
        @DisplayName("도쿄 편의점 ¥1,280, 환율 9.2 — 엔화처럼 1보다 작은 단위 환율도 그대로 남는다")
        void tokyoJpy() {
            Expense e = pay(11_776L, new BigDecimal("1280"), "JPY", new BigDecimal("9.2"));

            assertThat(e.getOriginalCurrency()).isEqualTo("JPY");
            assertThat(e.getOriginalAmount().multiply(e.getExchangeRate())
                .setScale(0, RoundingMode.HALF_UP).longValueExact()).isEqualTo(11_776L);
        }

        @Test
        @DisplayName("넷플릭스 정기결제 €12.99, 환율 1,512.35 — 소수 6자리 환율도 보존된다")
        void netflixEur() {
            Expense e = pay(19_645L, new BigDecimal("12.99"), "EUR", new BigDecimal("1512.350000"));

            assertThat(e.getExchangeRate()).isEqualByComparingTo("1512.35");
            assertThat(e.getOriginalAmount()).isEqualByComparingTo("12.99");
        }

        @Test
        @DisplayName("통화 코드는 대문자로 정규화 — usd 로 넣어도 USD 로 남는다")
        void normalizesCurrencyCode() {
            assertThat(pay(7_700L, new BigDecimal("5.50"), "usd", new BigDecimal("1400"))
                .getOriginalCurrency()).isEqualTo("USD");
        }
    }

    @Nested
    @DisplayName("원화 결제")
    class DomesticPayment {

        @Test
        @DisplayName("국내 편의점 4,500원 — 원 통화 정보가 전혀 남지 않는다")
        void plainKrw() {
            Expense e = pay(4_500L, null, null, null);

            assertThat(e.isForeignCurrency()).isFalse();
            assertThat(e.getOriginalAmount()).isNull();
            assertThat(e.getOriginalCurrency()).isNull();
            assertThat(e.getExchangeRate()).isNull();
        }

        @Test
        @DisplayName("통화에 KRW 를 넣어도 외화가 아니다 — 환율 1을 기록으로 남기지 않는다")
        void krwCurrencyIsNotForeign() {
            Expense e = pay(4_500L, new BigDecimal("4500"), "KRW", BigDecimal.ONE);

            assertThat(e.isForeignCurrency()).isFalse();
            assertThat(e.getOriginalCurrency()).isNull();
            assertThat(e.getExchangeRate()).isNull();
        }
    }

    @Nested
    @DisplayName("반쪽짜리 입력 방어")
    class PartialInput {

        @Test
        @DisplayName("통화만 오고 금액이 없으면 전부 비운다 — \"USD 를 환율 1,400 에\" 는 해석 불가")
        void currencyWithoutAmount() {
            Expense e = pay(7_700L, null, "USD", new BigDecimal("1400"));

            assertThat(e.isForeignCurrency()).isFalse();
            assertThat(e.getExchangeRate()).isNull();
        }

        @Test
        @DisplayName("금액만 오고 통화가 없으면 전부 비운다")
        void amountWithoutCurrency() {
            Expense e = pay(7_700L, new BigDecimal("5.50"), null, new BigDecimal("1400"));

            assertThat(e.getOriginalAmount()).isNull();
        }

        @Test
        @DisplayName("원 통화 금액이 0이면 결제가 아니다 — 비운다")
        void zeroOriginalAmount() {
            assertThat(pay(0L, BigDecimal.ZERO, "USD", new BigDecimal("1400"))
                .isForeignCurrency()).isFalse();
        }

        @Test
        @DisplayName("빈 통화 문자열도 원화로 본다")
        void blankCurrency() {
            assertThat(pay(7_700L, new BigDecimal("5.50"), "  ", new BigDecimal("1400"))
                .isForeignCurrency()).isFalse();
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("환율을 잘못 넣어 고칠 때 — 1,400 → 1,380 으로 갱신된다")
        void correctsExchangeRate() {
            Expense e = pay(7_700L, new BigDecimal("5.50"), "USD", new BigDecimal("1400"));

            e.updateExpense(null, null, ExpenseType.EXPENSE, 7_590L, "해외 결제", PAID_AT,
                null, "CREDIT_CARD", null, null,
                new BigDecimal("5.50"), "USD", new BigDecimal("1380"));

            assertThat(e.getAmount()).isEqualTo(7_590L);
            assertThat(e.getExchangeRate()).isEqualByComparingTo("1380");
        }

        @Test
        @DisplayName("해외 결제를 국내 결제로 고치면 원 통화 기록이 지워진다")
        void foreignToDomestic() {
            Expense e = pay(7_700L, new BigDecimal("5.50"), "USD", new BigDecimal("1400"));

            e.updateExpense(null, null, ExpenseType.EXPENSE, 7_700L, "국내 결제", PAID_AT,
                null, "CREDIT_CARD", null, null, null, null, null);

            assertThat(e.isForeignCurrency()).isFalse();
            assertThat(e.getOriginalAmount()).isNull();
            assertThat(e.getOriginalCurrency()).isNull();
            assertThat(e.getExchangeRate()).isNull();
        }
    }

    @Nested
    @DisplayName("다른 규칙과 함께 쓸 때")
    class WithOtherRules {

        @Test
        @DisplayName("해외 3개월 할부 $600(84만원) — 할부 분할과 원 통화 기록이 함께 산다")
        void foreignInstallment() {
            Expense e = Expense.createExpense(
                null, null, null, ExpenseType.EXPENSE, 840_000L,
                "면세점", PAID_AT, null, "CREDIT_CARD", 3, null,
                new BigDecimal("600"), "USD", new BigDecimal("1400"));

            assertThat(e.isInstallment()).isTrue();
            assertThat(e.isForeignCurrency()).isTrue();
            assertThat(e.installmentAmountAt(1)).isEqualTo(280_000L);
            assertThat(e.installmentAmountAt(3)).isEqualTo(280_000L);
        }

        @Test
        @DisplayName("해외 결제 환불 $5.50(7,700원) — 지출을 상계하면서 원 통화도 남는다")
        void foreignRefund() {
            Expense refund = Expense.createExpense(
                null, null, null, ExpenseType.INCOME, 7_700L,
                "해외 결제 취소", PAID_AT, null, "CREDIT_CARD", null, 42L,
                new BigDecimal("5.50"), "USD", new BigDecimal("1400"));

            assertThat(refund.isRefund()).isTrue();
            assertThat(refund.expenseContribution()).isEqualTo(-7_700L);
            assertThat(refund.incomeContribution()).isZero();          // 수입으로 잡히면 안 된다
            assertThat(refund.getOriginalCurrency()).isEqualTo("USD");
        }
    }
}
