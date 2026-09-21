package com.porest.desk.card.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 닫힌 회차 판정의 순수 계산 — DB 없이 규칙만 잠근다("결제가 끝난 회차는 기록만", D1~D16).
 *
 * <p>돈이 실제로 어떻게 움직이는지는 {@code ClosedCycleRulesScenarioTest} 가 끝까지 돌려 본다.
 * 여기서는 판정의 갈래 — 생성(닫힌 회차 몫 → 기록용)과 제거(기록용 → 상계 −, 결제분 → 붙잡기 후보,
 * 열린 회차 → 선결제 환급 후보) — 를 짚는다.
 */
class CardCycleMathTest {

    private static final long CARD = 9L;
    private static final long OTHER_CARD = 10L;
    /** 결제일 12일 — 오늘(9/14) 기준 8월 회차는 9/12 에 결제돼 닫혔고, 9월 회차는 10/12 결제라 열려 있다. */
    private static final PaymentSchedule DAY_12 = PaymentSchedule.of(12);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUG = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    private static final LocalDate AUG_END = LocalDate.of(2026, 8, 31);

    private static CardCycleMath.Side side(Map<LocalDate, Long> dues, LocalDate mark) {
        return new CardCycleMath.Side(CARD, DAY_12, dues, mark);
    }

    @Test
    @DisplayName("닫힌 회차 결제분을 지우면 돌려주지 않고 붙잡을 후보로 남긴다(D1)")
    void closedPaidRemovalIsHeldCandidate() {
        var plan = CardCycleMath.plan(side(Map.of(AUG, 50_000L), null), CardCycleMath.Side.none(), TODAY);

        assertThat(plan.paidRemovals()).singleElement().satisfies(r -> {
            assertThat(r.closed()).isTrue();
            assertThat(r.amount()).isEqualTo(50_000L);
            assertThat(r.cycleStart()).isEqualTo(AUG);
        });
        assertThat(plan.settleDeltaBefore()).as("상계는 호출자가 결제가 덮은 만큼 붙잡는다").isZero();
        assertThat(plan.newMark()).isNull();
    }

    @Test
    @DisplayName("기록용을 지우면 받쳐 둔 상계만 걷는다 — 돌려줄 후보가 아니다")
    void recordRemovalWithdrawsSettle() {
        var plan = CardCycleMath.plan(side(Map.of(AUG, 25_000L), AUG_END), CardCycleMath.Side.none(), TODAY);

        assertThat(plan.paidRemovals()).isEmpty();
        assertThat(plan.settleDeltaBefore()).isEqualTo(-25_000L);
    }

    @Test
    @DisplayName("열린 회차에서 빠진 몫은 선결제 환급 후보(D3)")
    void openRemovalIsRefundCandidate() {
        var plan = CardCycleMath.plan(side(Map.of(SEP, 50_000L), null), CardCycleMath.Side.none(), TODAY);

        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.closed()).isFalse());
        assertThat(plan.settleDeltaBefore()).isZero();
    }

    @Test
    @DisplayName("결제일 당일 입력도 닫힌 회차 — 기록용, 당일 추가 결제 없음(D2·U7)")
    void paymentDayItselfIsClosed() {
        var plan = CardCycleMath.plan(CardCycleMath.Side.none(),
            side(Map.of(AUG, 40_000L), null), LocalDate.of(2026, 9, 12));

        assertThat(plan.settleDeltaAfter()).isEqualTo(40_000L);
        assertThat(plan.newRecordAmount()).isEqualTo(40_000L);
        assertThat(plan.newMark()).isEqualTo(AUG_END);
    }

    @Test
    @DisplayName("할부 소급 입력 — 닫힌 회차분만 기록용, 표식은 마지막 닫힌 회차 말일(R5)")
    void installmentCreate() {
        var plan = CardCycleMath.plan(CardCycleMath.Side.none(),
            side(Map.of(JUL, 30_000L, AUG, 30_000L, SEP, 30_000L), null), TODAY);

        assertThat(plan.newRecordAmount()).isEqualTo(60_000L);
        assertThat(plan.settleDeltaAfter()).isEqualTo(60_000L);
        assertThat(plan.newMark()).isEqualTo(AUG_END);
        assertThat(plan.paidRemovals()).isEmpty();
    }

    @Test
    @DisplayName("열린 회차 거래를 닫힌 회차 날짜로 옮기면 빠짐(환급 후보) + 새 기록용(2-1 ②)")
    void openToClosedMove() {
        var plan = CardCycleMath.plan(side(Map.of(SEP, 50_000L), null), side(Map.of(AUG, 50_000L), null), TODAY);

        assertThat(plan.paidRemovals()).singleElement().satisfies(r -> {
            assertThat(r.closed()).isFalse();
            assertThat(r.cycleStart()).isEqualTo(SEP);
        });
        assertThat(plan.settleDeltaBefore()).isEqualTo(50_000L);
        assertThat(plan.newRecordAmount()).isEqualTo(50_000L);
        assertThat(plan.newMark()).isEqualTo(AUG_END);
        assertThat(plan.afterBillable()).as("기록용 몫은 청구가 아니다").isEmpty();
    }

    @Test
    @DisplayName("다른 카드의 닫힌 회차로 옮기면 새 카드 쪽 상계로 기록용이 된다")
    void moveToOtherCardClosed() {
        var plan = CardCycleMath.plan(side(Map.of(SEP, 20_000L), null),
            new CardCycleMath.Side(OTHER_CARD, DAY_12, Map.of(AUG, 20_000L), null), TODAY);

        assertThat(plan.settleDeltaBefore()).isZero();
        assertThat(plan.settleDeltaAfter()).isEqualTo(20_000L);
        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.cardRowId()).isEqualTo(CARD));
        assertThat(plan.newMark()).isEqualTo(AUG_END);
    }

    @Test
    @DisplayName("닫힌 회차 날짜의 카드 수입 — 생길 때 상계 −, 지울 때 상계 + 로 카드 잔액이 그대로다(2-1 ⑤)")
    void closedCycleIncomeMovesOnlySettle() {
        var created = CardCycleMath.plan(CardCycleMath.Side.none(), side(Map.of(AUG, -10_000L), null), TODAY);
        assertThat(created.settleDeltaAfter()).isEqualTo(-10_000L);
        assertThat(created.newRecordAmount()).as("수입은 기록용 금액에 안 센다").isZero();
        assertThat(created.newMark()).isEqualTo(AUG_END);

        var removed = CardCycleMath.plan(side(Map.of(AUG, -10_000L), AUG_END), CardCycleMath.Side.none(), TODAY);
        assertThat(removed.settleDeltaBefore()).isEqualTo(10_000L);
        assertThat(removed.paidRemovals()).isEmpty();

        // 이 규칙 전에 적힌(표식 없는) 닫힌 회차 수입도 청구를 줄였던 몫이다 — 지우면 상계로 되받친다.
        var legacy = CardCycleMath.plan(side(Map.of(AUG, -10_000L), null), CardCycleMath.Side.none(), TODAY);
        assertThat(legacy.settleDeltaBefore()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("열린 회차 카드 수입은 청구를 줄인다 — 선결제가 남으면 돌려줄 후보(D3)")
    void openIncomeIsRefundCandidate() {
        var plan = CardCycleMath.plan(CardCycleMath.Side.none(), side(Map.of(SEP, -10_000L), null), TODAY);

        assertThat(plan.paidRemovals()).singleElement().satisfies(r -> {
            assertThat(r.closed()).isFalse();
            assertThat(r.amount()).isEqualTo(10_000L);
        });
        assertThat(plan.settleDeltaAfter()).isZero();
    }

    @Test
    @DisplayName("분류만 고친 기록용은 표식을 지킨다")
    void unchangedRecordKeepsMark() {
        var plan = CardCycleMath.plan(side(Map.of(AUG, 25_000L), AUG_END), side(Map.of(AUG, 25_000L), AUG_END), TODAY);

        assertThat(plan.newMark()).isEqualTo(AUG_END);
        assertThat(plan.settleDeltaBefore()).isZero();
        assertThat(plan.paidRemovals()).isEmpty();
    }

    @Test
    @DisplayName("열린 회차에 붙은 옛 재청구 방지 표식(D14 폐지) — 청구에 없던 몫이라 상계로만 정리한다")
    void legacyOpenMarkIsSettledOnly() {
        LocalDate sepEnd = LocalDate.of(2026, 9, 30);
        var plan = CardCycleMath.plan(side(Map.of(SEP, 30_000L), sepEnd), CardCycleMath.Side.none(), TODAY);

        assertThat(plan.settleDeltaBefore()).isEqualTo(-30_000L);
        assertThat(plan.paidRemovals()).isEmpty();
    }

    @Test
    @DisplayName("결제일 없는 옛 카드는 회차가 닫히지 않는다 — 늘 열린 회차(D8)")
    void noPaymentDayIsAlwaysOpen() {
        var noDay = new CardCycleMath.Side(CARD, PaymentSchedule.of(null), Map.of(JUL, 50_000L), null);

        assertThat(noDay.isCard()).isTrue();
        var plan = CardCycleMath.plan(noDay, CardCycleMath.Side.none(), TODAY);
        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.closed()).isFalse());
        assertThat(plan.newMark()).isNull();
    }

    @Test
    @DisplayName("열린 회차 금액만 줄이면 그 차이가 환급 후보, 바뀐 뒤 청구 몫은 afterBillable 로")
    void openReduction() {
        var plan = CardCycleMath.plan(side(Map.of(SEP, 50_000L), null), side(Map.of(SEP, 30_000L), null), TODAY);

        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.amount()).isEqualTo(20_000L));
        assertThat(plan.afterBillable()).containsEntry(SEP, 30_000L);
    }

    @Test
    @DisplayName("다음 회차(10월)로 미뤄도 열린 회차끼리라 기록용이 없다")
    void openToOpen() {
        var plan = CardCycleMath.plan(side(Map.of(SEP, 50_000L), null), side(Map.of(OCT, 50_000L), null), TODAY);

        assertThat(plan.newMark()).isNull();
        assertThat(plan.newRecordAmount()).isZero();
        assertThat(plan.paidRemovals()).singleElement()
            .satisfies(r -> assertThat(r.cycleStart()).isEqualTo(SEP));
    }
}
