package com.porest.desk.card.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 회차별 결제일(D5) — 결제일을 바꿔도 이미 정해진 회차의 결제일은 그대로다. */
class PaymentScheduleTest {

    private static final LocalDate BEGIN = LocalDate.of(1970, 1, 1);
    private static final LocalDate AUG = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("결제일은 다음 달, 그 달에 없는 날이면 말일")
    void paymentDateClampsToMonthEnd() {
        assertThat(PaymentSchedule.of(31).paymentDateOf(LocalDate.of(2027, 1, 1)))
            .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(PaymentSchedule.of(12).paymentDateOf(AUG)).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("결제일 당일부터 닫힌 회차(D2)")
    void closesOnPaymentDay() {
        PaymentSchedule s = PaymentSchedule.of(12);

        assertThat(s.isClosed(AUG, LocalDate.of(2026, 9, 11))).isFalse();
        assertThat(s.isClosed(AUG, LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(s.closedThrough(LocalDate.of(2026, 9, 11))).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(s.closedThrough(LocalDate.of(2026, 9, 12))).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("다가오는 결제는 결제일이 오늘보다 뒤인 첫 회차, 그 다음 회차도 이력을 따른다")
    void upcomingAndFollowing() {
        PaymentSchedule s = PaymentSchedule.of(12);

        assertThat(s.firstOpenPaymentDate(LocalDate.of(2026, 9, 11))).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(s.firstOpenPaymentDate(LocalDate.of(2026, 9, 12))).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(s.followingPaymentDate(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    @DisplayName("25일 → 5일: 결제 전이던 8월분은 9/25, 그 다음 9월분부터 10/5(D5)")
    void changeTo5AppliesFromNextCycle() {
        PaymentSchedule s = new PaymentSchedule(List.of(
            new PaymentSchedule.Entry(BEGIN, 25), new PaymentSchedule.Entry(SEP, 5)), 5);

        assertThat(s.paymentDateOf(AUG)).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(s.paymentDateOf(SEP)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(s.firstOpenPaymentDate(LocalDate.of(2026, 9, 21))).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(s.followingPaymentDate(LocalDate.of(2026, 9, 25))).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(s.cycleDueOn(LocalDate.of(2026, 9, 25))).isEqualTo(AUG);
        assertThat(s.cycleDueOn(LocalDate.of(2026, 10, 5))).isEqualTo(SEP);
        assertThat(s.cycleDueOn(LocalDate.of(2026, 10, 25))).as("새 결제일이 이긴다").isNull();
    }

    @Test
    @DisplayName("5일 → 25일: 9월분은 10/5 그대로, 10월분부터 11/25")
    void changeTo25AppliesFromNextCycle() {
        PaymentSchedule s = new PaymentSchedule(List.of(
            new PaymentSchedule.Entry(BEGIN, 5), new PaymentSchedule.Entry(OCT, 25)), 25);

        assertThat(s.paymentDateOf(SEP)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(s.paymentDateOf(OCT)).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(s.closedThrough(LocalDate.of(2026, 10, 5))).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(s.closedThrough(LocalDate.of(2026, 11, 24))).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("이력보다 이른 회차는 첫 이력의 결제일로 본다")
    void beforeFirstEntryUsesFirstDay() {
        PaymentSchedule s = new PaymentSchedule(List.of(new PaymentSchedule.Entry(SEP, 7)), 9);

        assertThat(s.dayFor(AUG)).isEqualTo(7);
    }

    @Test
    @DisplayName("결제일 없는 카드는 회차가 닫히지 않는다(D8)")
    void noDayNeverCloses() {
        PaymentSchedule s = PaymentSchedule.of(null);

        assertThat(s.hasDay()).isFalse();
        assertThat(s.isClosed(AUG, LocalDate.of(2030, 1, 1))).isFalse();
        assertThat(s.closedThrough(LocalDate.of(2026, 9, 21))).isNull();
        assertThat(s.firstOpenPaymentDate(LocalDate.of(2026, 9, 21))).isNull();
        assertThat(s.cycleDueOn(LocalDate.of(2026, 9, 21))).isNull();
    }
}
