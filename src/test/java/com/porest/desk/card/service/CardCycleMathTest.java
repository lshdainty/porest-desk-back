package com.porest.desk.card.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 닫힌 회차 판정의 순수 계산 — DB 없이 규칙만 잠근다.
 *
 * <p>돈이 실제로 어떻게 움직이는지는 {@code ClosedCycleRulesScenarioTest} 가 끝까지 돌려 본다.
 * 여기서는 그 시나리오로 만들기 번거로운 갈래(상계 없는 기록용, 말일 보정)를 짚는다.
 */
class CardCycleMathTest {

    private static final long CARD = 9L;
    private static final int DAY = 12;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUG = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);

    private static CardCycleMath.Side side(Map<LocalDate, Long> dues, LocalDate mark) {
        return new CardCycleMath.Side(CARD, DAY, dues, mark);
    }

    @Test
    @DisplayName("결제일은 다음 달, 그 달에 없는 날이면 말일 — 닫힘은 결제일 다음 날부터(R1)")
    void paymentDateAndClosing() {
        assertThat(CardCycleMath.paymentDateOf(LocalDate.of(2027, 1, 1), 31))
            .isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(CardCycleMath.isClosed(AUG, DAY, LocalDate.of(2026, 9, 12))).isFalse();
        assertThat(CardCycleMath.isBilled(AUG, DAY, LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(CardCycleMath.isPaymentToday(AUG, DAY, LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(CardCycleMath.isClosed(AUG, DAY, LocalDate.of(2026, 9, 13))).isTrue();
    }

    @Test
    @DisplayName("환급 기한은 결제일이 속한 달의 말일까지(R6)")
    void refundWindow() {
        assertThat(CardCycleMath.refundableUntil(AUG, DAY)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(CardCycleMath.refundWindowOpen(AUG, DAY, LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(CardCycleMath.refundWindowOpen(AUG, DAY, LocalDate.of(2026, 10, 1))).isFalse();
    }

    @Test
    @DisplayName("닫힌 회차끼리 옮긴 결제분은 짝을 지어 돈이 안 움직인다(R7)")
    void closedToClosedPairs() {
        var plan = CardCycleMath.plan(
            side(Map.of(AUG, 100_000L), null), side(Map.of(JUL, 100_000L), null), true, 0L, TODAY);

        assertThat(plan.paidRemovals()).isEmpty();
        assertThat(plan.recordRefunds()).isEmpty();
        assertThat(plan.settleDeltaBefore()).as("옛 회차 결제가 이미 덮었다 — 상계 없음").isZero();
        assertThat(plan.newMark()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(plan.newRecordAmount()).isZero();
    }

    @Test
    @DisplayName("상계로 붙잡은 기록용이 열린 회차로 가면 정상 청구 — 상계를 걷는다(케이스 10)")
    void backedRecordToOpen() {
        var plan = CardCycleMath.plan(
            side(Map.of(AUG, 25_000L), LocalDate.of(2026, 8, 31)), side(Map.of(SEP, 25_000L), null),
            true, 25_000L, TODAY);

        assertThat(plan.settleDeltaBefore()).isEqualTo(-25_000L);
        assertThat(plan.newMark()).isNull();
        assertThat(plan.afterBillable()).containsEntry(SEP, 25_000L);
    }

    @Test
    @DisplayName("상계 없이 기록용이 된 몫(앱이 결제한 돈)은 열린 회차로 가도 기록용 — 두 번 내지 않는다")
    void unbackedRecordToOpenStaysRecord() {
        var plan = CardCycleMath.plan(
            side(Map.of(AUG, 25_000L), LocalDate.of(2026, 8, 31)), side(Map.of(SEP, 25_000L), null),
            true, 0L, TODAY);

        assertThat(plan.settleDeltaBefore()).isZero();
        assertThat(plan.newMark()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(plan.afterBillable()).isEmpty();
    }

    @Test
    @DisplayName("기한 지난 결제분 삭제 — 돌려주지 않는 후보로 남긴다(호출자가 붙잡는다)")
    void expiredPaidRemoval() {
        var plan = CardCycleMath.plan(
            side(Map.of(JUL, 50_000L), null), CardCycleMath.Side.none(), true, 0L, TODAY);

        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.refundable()).isFalse());
        assertThat(plan.windowClosedRemoval()).isTrue();
    }

    @Test
    @DisplayName("결제일 전 회차에서 빠진 몫도 후보 — 선결제가 덮었으면 돌려준다(결함 2)")
    void unbilledRemovalIsCandidate() {
        var plan = CardCycleMath.plan(
            side(Map.of(SEP, 50_000L), null), CardCycleMath.Side.none(), true, 0L, TODAY);

        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.refundable()).isTrue());
    }

    @Test
    @DisplayName("할부 소급 입력 — 닫힌 회차분만 기록용, 표식은 마지막 닫힌 회차 말일(R5)")
    void installmentCreate() {
        var plan = CardCycleMath.plan(CardCycleMath.Side.none(),
            side(Map.of(JUL, 30_000L, AUG, 30_000L, SEP, 30_000L), null), false, 0L, TODAY);

        assertThat(plan.newRecordAmount()).isEqualTo(60_000L);
        assertThat(plan.settleDeltaAfter()).isEqualTo(60_000L);
        assertThat(plan.newMark()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("결제계좌 없는 카드의 기록용 삭제 — 돌려주지 않고 상계만 걷는다")
    void recordRemovalWithoutAccount() {
        var plan = CardCycleMath.plan(
            side(Map.of(AUG, 25_000L), LocalDate.of(2026, 8, 31)), CardCycleMath.Side.none(),
            false, 25_000L, TODAY);

        assertThat(plan.recordRefunds()).isEmpty();
        assertThat(plan.recordHeld()).isEqualTo(25_000L);
        assertThat(plan.settleDeltaBefore()).isEqualTo(-25_000L);
    }
}
