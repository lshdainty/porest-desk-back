package com.porest.desk.asset.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이체 값 규칙의 진리표.
 *
 * <p>한 벌이 두 묶음이라는 것 자체를 여기서 잠근다 — <b>당사자</b>는 이체를 만들 때도 적어 둘
 * 때도 참이어야 하고, <b>금액</b>은 금액이 있을 때만 물을 수 있다. 둘을 한 함수에 묶어 뒀다가
 * 금액을 비워 두는 것이 정상인 이체 프리셋이 400 이 됐다(2026-09-15, #169).
 */
class AssetTransferRulesTest {

    private static Object codeOf(Throwable e) {
        return ((InvalidValueException) e).getErrorCode();
    }

    @Nested
    @DisplayName("당사자 — 금액과 무관하게 언제나 본다")
    class Parties {

        @Test
        @DisplayName("한쪽이라도 없으면 거절")
        void rejectsMissing() {
            assertThatThrownBy(() -> AssetTransferRules.validateParties(null, 2L))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.REQUIRED_VALUE_MISSING);
            assertThatThrownBy(() -> AssetTransferRules.validateParties(1L, null))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("같은 자산끼리는 거절 — 잘못된 잔액 이력이 남는다")
        void rejectsSame() {
            assertThatThrownBy(() -> AssetTransferRules.validateParties(1L, 1L))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_SAME_ASSET);
        }

        @Test
        @DisplayName("서로 다른 두 자산이면 통과")
        void allowsDistinct() {
            assertThatCode(() -> AssetTransferRules.validateParties(1L, 2L))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("금액 — 금액이 있을 때만 물을 수 있다")
    class Money {

        @Test
        @DisplayName("금액이 없거나 0 이하면 거절")
        void rejectsMissingOrNonPositive() {
            assertThatThrownBy(() -> AssetTransferRules.validateMoney(null, null, null))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_INVALID_AMOUNT);
            assertThatThrownBy(() -> AssetTransferRules.validateMoney(0L, null, null))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("음수 수수료는 수수료 코드로 거절한다 — 금액 얘기를 하면 고칠 자리를 못 찾는다")
        void rejectsNegativeFeeWithItsOwnCode() {
            assertThatThrownBy(() -> AssetTransferRules.validateMoney(100_000L, -1L, null))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_INVALID_FEE);
        }

        @Test
        @DisplayName("이자는 상환액을 넘을 수 없다. 같은 값은 통과 — 이자만 내는 거치 상환")
        void interestBoundedByAmount() {
            assertThatThrownBy(() -> AssetTransferRules.validateMoney(100_000L, null, 100_001L))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_INVALID_INTEREST);
            assertThat(AssetTransferRules.validateMoney(100_000L, null, 100_000L))
                .isEqualTo(100_000L);
        }

        @Test
        @DisplayName("이자를 안 주면 0 으로 정규화한다")
        void normalizesNullInterest() {
            assertThat(AssetTransferRules.validateMoney(100_000L, 500L, null)).isZero();
        }
    }

    @Nested
    @DisplayName("부호만 — 금액이 없는 프리셋이 지난다")
    class Signs {

        @Test
        @DisplayName("음수 수수료는 수수료 코드, 음수 이자는 이자 코드")
        void rejectsNegatives() {
            assertThatThrownBy(() -> AssetTransferRules.validateMoneySigns(-1L, null))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_INVALID_FEE);
            assertThatThrownBy(() -> AssetTransferRules.validateMoneySigns(null, -1L))
                .isInstanceOf(InvalidValueException.class)
                .extracting(AssetTransferRulesTest::codeOf)
                .isEqualTo(DeskErrorCode.ASSET_TRANSFER_INVALID_INTEREST);
        }

        @Test
        @DisplayName("견줄 금액이 없으니 이자 상한은 안 본다 — 그 조합은 실제로 이체할 때 잡힌다")
        void doesNotBoundInterestWithoutAmount() {
            assertThatCode(() -> AssetTransferRules.validateMoneySigns(500L, 9_999_999L))
                .doesNotThrowAnyException();
        }
    }
}
