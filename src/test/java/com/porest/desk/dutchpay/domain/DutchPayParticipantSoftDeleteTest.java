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

    private DutchPayParticipant participant(DutchPay dp, String name, long amount) {
        DutchPayParticipant p = DutchPayParticipant.create(dp, null, name, amount);
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
}
