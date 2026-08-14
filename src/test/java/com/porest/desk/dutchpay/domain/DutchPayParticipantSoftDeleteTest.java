package com.porest.desk.dutchpay.domain;

import com.porest.core.type.YNType;
import com.porest.desk.dutchpay.type.SplitMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 더치페이 참가자 soft-delete 회귀 방지 — 참가자는 물리 삭제 대신 is_deleted 플래그로 관리하고,
 * 부모(DutchPay) 삭제 시 활성 참가자도 함께 soft-delete 된다(cascade).
 */
class DutchPayParticipantSoftDeleteTest {

    private DutchPay dutchPay() {
        return DutchPay.createDutchPay(null, null, "점심", null, 10_000L, "KRW",
                SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1));
    }

    /** 갚을 참여자. 결제자는 {@link #payer} 로 따로 만든다. */
    private DutchPayParticipant participant(DutchPay dp, String name, long amount) {
        DutchPayParticipant p = DutchPayParticipant.create(dp, null, name, amount, false);
        dp.addParticipant(p);
        return p;
    }

    @Test
    @DisplayName("deleteDutchPay — 부모 삭제 시 활성 참가자도 cascade soft-delete 된다")
    void deleteCascadesToParticipants() {
        DutchPay dp = dutchPay();
        DutchPayParticipant a = participant(dp, "A", 5_000L);
        DutchPayParticipant b = participant(dp, "B", 5_000L);

        dp.deleteDutchPay();

        assertThat(dp.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(a.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(b.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(dp.getActiveParticipants()).isEmpty();
    }

    @Test
    @DisplayName("clearParticipants — 교체 시 기존 활성 참가자는 soft-delete, 새 참가자만 활성으로 남는다")
    void clearSoftDeletesAndKeepsNewActive() {
        DutchPay dp = dutchPay();
        DutchPayParticipant old = participant(dp, "old", 10_000L);

        dp.clearParticipants();
        DutchPayParticipant fresh = participant(dp, "new", 10_000L);

        assertThat(old.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(dp.getActiveParticipants()).containsExactly(fresh);
    }

    @Test
    @DisplayName("checkSettled — 활성 참가자만 정산 판정 대상이다")
    void checkSettledConsidersActiveOnly() {
        DutchPay dp = dutchPay();
        DutchPayParticipant paid = participant(dp, "paid", 5_000L);
        DutchPayParticipant deletedUnpaid = participant(dp, "deleted", 5_000L);
        paid.markPaid();
        deletedUnpaid.deleteParticipant(); // 삭제된 미납 참가자는 정산 판정에서 제외

        dp.checkSettled();

        assertThat(dp.getIsSettled()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("settleAll — 활성 전원 markPaid·isSettled=Y, 금액은 입력 그대로 보존")
    void settleAllMarksAllAndPreservesAmounts() {
        DutchPay dp = dutchPay();
        participant(dp, "A", 12_000L);
        participant(dp, "B", 10_000L);
        participant(dp, "C", 8_000L);

        dp.settleAll();

        assertThat(dp.getIsSettled()).isEqualTo(YNType.Y);
        assertThat(dp.getActiveParticipants()).allMatch(p -> p.getIsPaid() == YNType.Y);
        // settleAll 은 금액을 재계산/변경하지 않음 → 입력 합 12,000+10,000+8,000=30,000 보존
        assertThat(dp.getActiveParticipants().stream().mapToLong(DutchPayParticipant::getAmount).sum())
                .isEqualTo(30_000L);
    }

    @Test
    @DisplayName("checkSettled — 일부 납부면 N, 전원 납부면 Y 로 전이")
    void checkSettledPartialThenAll() {
        DutchPay dp = dutchPay();
        DutchPayParticipant a = participant(dp, "A", 10_000L);
        DutchPayParticipant b = participant(dp, "B", 10_000L);
        DutchPayParticipant c = participant(dp, "C", 10_000L);

        a.markPaid();
        dp.checkSettled();
        assertThat(dp.getIsSettled()).isEqualTo(YNType.N);

        b.markPaid();
        c.markPaid();
        dp.checkSettled();
        assertThat(dp.getIsSettled()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("checkSettled — 활성 0명이면 N (빈 목록 vacuous-true 가드)")
    void checkSettledEmptyIsNotSettled() {
        DutchPay dp = dutchPay();
        DutchPayParticipant only = participant(dp, "A", 10_000L);
        only.deleteParticipant(); // 유일 참가자 soft-delete → 활성 0명

        dp.checkSettled();

        assertThat(dp.getIsSettled()).isEqualTo(YNType.N);
    }
}
