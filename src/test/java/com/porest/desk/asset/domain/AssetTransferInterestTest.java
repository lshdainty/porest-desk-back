package com.porest.desk.asset.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대출 상환의 원금/이자 분리 — 실제 상환 명세서를 그대로 넣어 검증한다.
 *
 * <p>핵심 규칙: 상환액(amount) 중 <b>이자는 부채를 줄이지 않는다</b>. 원금은 부채가 줄어드는
 * 자산 이동이지만 이자는 은행으로 아예 나가는 비용이라 성격이 다르다.
 * 이걸 구분하지 않으면 부채가 실제보다 빨리 줄어 순자산이 좋게 나오고, 이자는 지출에서 사라진다.
 */
@DisplayName("대출 상환 원금/이자")
class AssetTransferInterestTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 7, 25, 9, 0);

    private AssetTransfer repay(long amount, Long interest) {
        return AssetTransfer.createTransfer(
            null, null, null, amount, 0L, interest, "대출 상환", PAID_AT);
    }

    @Nested
    @DisplayName("주택담보대출 상환")
    class Mortgage {

        @Test
        @DisplayName("50만원 상환(이자 15만) — 부채는 35만원만 줄고 이자 15만은 지출로 빠진다")
        void repay500kWithInterest150k() {
            AssetTransfer t = repay(500_000L, 150_000L);

            assertThat(t.hasInterest()).isTrue();
            assertThat(t.getInterestAmount()).isEqualTo(150_000L);
            assertThat(t.principalAmount()).isEqualTo(350_000L);
        }

        @Test
        @DisplayName("원리금균등 초반 — 상환 87만 중 이자가 62만이면 원금은 25만만 준다")
        void earlyStageHeavyInterest() {
            AssetTransfer t = repay(870_000L, 620_000L);

            assertThat(t.principalAmount()).isEqualTo(250_000L);
        }

        @Test
        @DisplayName("원리금균등 후반 — 같은 87만이어도 이자 5만이면 원금이 82만 준다")
        void lateStageLowInterest() {
            AssetTransfer t = repay(870_000L, 50_000L);

            assertThat(t.principalAmount()).isEqualTo(820_000L);
        }
    }

    @Nested
    @DisplayName("이자만 내는 거치 상환")
    class InterestOnly {

        @Test
        @DisplayName("거치기간에 이자 25만만 내면 원금은 그대로 — 부채가 전혀 줄지 않는다")
        void interestOnlyLeavesPrincipalIntact() {
            AssetTransfer t = repay(250_000L, 250_000L);

            assertThat(t.hasInterest()).isTrue();
            assertThat(t.principalAmount()).isZero();
        }
    }

    @Nested
    @DisplayName("이자가 없는 이체")
    class NoInterest {

        @Test
        @DisplayName("일반 계좌 이체 100만원 — 이자 0, 전액이 상대 계좌로 간다")
        void plainTransfer() {
            AssetTransfer t = repay(1_000_000L, 0L);

            assertThat(t.hasInterest()).isFalse();
            assertThat(t.principalAmount()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("이자를 안 넘기면(null) 0 으로 본다 — 기존 이체와 계산이 같다")
        void nullInterestTreatedAsZero() {
            AssetTransfer t = repay(1_000_000L, null);

            assertThat(t.getInterestAmount()).isZero();
            assertThat(t.hasInterest()).isFalse();
            assertThat(t.principalAmount()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("무이자 할부금 상환 30만원 — 전액이 원금")
        void zeroInterestInstallment() {
            assertThat(repay(300_000L, 0L).principalAmount()).isEqualTo(300_000L);
        }
    }

    @Test
    @DisplayName("이자 지출 거래 연결을 걸어 둔다 — 이체를 지울 때 함께 무르기 위해")
    void linksInterestExpense() {
        AssetTransfer t = repay(500_000L, 150_000L);
        assertThat(t.getInterestExpenseRowId()).isNull();

        t.linkInterestExpense(777L);

        assertThat(t.getInterestExpenseRowId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("순자산 관점 — 통장에서 50만 나가고 부채가 35만 줄면 순자산은 이자만큼(15만) 준다")
    void netWorthDropsByInterestOnly() {
        AssetTransfer t = repay(500_000L, 150_000L);

        long fromDelta = -(t.getAmount() + t.getFee()); // 통장에서 나간 돈
        long toDelta = t.principalAmount();             // 부채 감소분(잔액 증가)
        assertThat(fromDelta + toDelta).isEqualTo(-150_000L);
    }
}
