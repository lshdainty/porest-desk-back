package com.porest.desk.asset.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부호 규약 — 사용자가 절대값으로 넣은 잔액에 <b>종류가</b> 부호를 씌운다(QA 2026-09-03 #17 #19 #21).
 */
@DisplayName("자산 부호 규약")
class AssetSignPolicyTest {

    @Nested
    @DisplayName("유형 분류 — 집계에서 자산군/부채군을 가르는 기준")
    class TypeClassification {

        @Test
        @DisplayName("신용카드·대출만 부채군")
        void debtTypes() {
            assertThat(AssetSignPolicy.isDebtType(AssetType.CREDIT_CARD)).isTrue();
            assertThat(AssetSignPolicy.isDebtType(AssetType.LOAN)).isTrue();
        }

        @Test
        @DisplayName("체크카드는 자산군 — 자체 잔액이 없어 연결 계좌에서 즉시 빠진다")
        void checkCardIsNotDebt() {
            assertThat(AssetSignPolicy.isDebtType(AssetType.CHECK_CARD)).isFalse();
        }

        @Test
        @DisplayName("입출금은 잔액이 음수(마이너스 통장)여도 자산군 — 유형이 기준이라 부호를 안 본다")
        void bankAccountIsNeverDebtType() {
            assertThat(AssetSignPolicy.isDebtType(AssetType.BANK_ACCOUNT)).isFalse();
            assertThat(AssetSignPolicy.isDebtType(AssetType.SAVINGS)).isFalse();
            assertThat(AssetSignPolicy.isDebtType(AssetType.CASH)).isFalse();
            assertThat(AssetSignPolicy.isDebtType(AssetType.INVESTMENT)).isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeBalance — API 경계에서 부호를 확정한다")
    class NormalizeBalance {

        @Test
        @DisplayName("대출은 양수로 넣어도 음수로 — 여러 번 걸어도 같다(멱등)")
        void loanAlwaysNegative() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.LOAN, false, 3_000_000L))
                .isEqualTo(-3_000_000L);
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.LOAN, false, -3_000_000L))
                .isEqualTo(-3_000_000L);
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.LOAN, null, 3_000_000L))
                .isEqualTo(-3_000_000L);
        }

        @Test
        @DisplayName("신용카드도 같다 — 사용액은 음수")
        void creditCardAlwaysNegative() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.CREDIT_CARD, null, 356_800L))
                .isEqualTo(-356_800L);
        }

        @Test
        @DisplayName("자산군은 양수로 — isOverdraft=false 면 음수를 보내도 절대값")
        void assetTypeBecomesPositive() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, false, -50_000L))
                .isEqualTo(50_000L);
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.SAVINGS, false, 1_000_000L))
                .isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("마이너스 통장은 쓴 금액을 양수로 받아 음수로 저장한다")
        void overdraftBecomesNegative() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, true, 50_000L))
                .isEqualTo(-50_000L);
            // 멱등 — 이미 음수인 값을 다시 걸어도 뒤집히지 않는다.
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, true, -50_000L))
                .isEqualTo(-50_000L);
        }

        @Test
        @DisplayName("isOverdraft 를 안 보내면(옛 클라이언트) 보낸 부호를 그대로 — 저장만 해도 뒤집히면 안 된다")
        void legacyClientSignRespected() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, null, -50_000L))
                .isEqualTo(-50_000L);
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, null, 50_000L))
                .isEqualTo(50_000L);
        }

        @Test
        @DisplayName("0 은 어느 쪽으로도 −0 이 되지 않는다")
        void zeroStaysZero() {
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.CREDIT_CARD, null, 0L)).isZero();
            assertThat(AssetSignPolicy.normalizeBalance(AssetType.BANK_ACCOUNT, true, 0L)).isZero();
        }
    }

    @Nested
    @DisplayName("normalizeAnchor — 이력 적재는 부채군만 내린다")
    class NormalizeAnchor {

        @Test
        @DisplayName("부채군은 음수로 내린다 — 컨트롤러를 안 거치는 경로의 마지막 방어선")
        void debtGoesNegative() {
            assertThat(AssetSignPolicy.normalizeAnchor(AssetType.LOAN, 5_000_000L))
                .isEqualTo(-5_000_000L);
            assertThat(AssetSignPolicy.normalizeAnchor(AssetType.CREDIT_CARD, 356_800L))
                .isEqualTo(-356_800L);
        }

        @Test
        @DisplayName("자산군은 손대지 않는다 — 여기서 abs() 를 걸면 경계에서 확정한 마이너스 통장이 다시 뒤집힌다")
        void assetTypeUntouched() {
            assertThat(AssetSignPolicy.normalizeAnchor(AssetType.BANK_ACCOUNT, -50_000L))
                .isEqualTo(-50_000L);
            assertThat(AssetSignPolicy.normalizeAnchor(AssetType.CHECK_CARD, 50_000L))
                .isEqualTo(50_000L);
        }
    }
}
