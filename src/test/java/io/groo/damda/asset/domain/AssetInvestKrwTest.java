package io.groo.damda.asset.domain;

import io.groo.core.type.YNType;
import io.groo.damda.asset.type.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 채널을 나눠 환산하는 {@code balanceInKrw(cash, holding)} — 예수금만 환율을 곱하고 평가금액은 원화 그대로. */
@DisplayName("투자 자산 원화 환산(예수금 / 평가금액 분리)")
class AssetInvestKrwTest {

    private Asset asset(AssetType type, String currency, BigDecimal rate) {
        return Asset.createAsset(null, "투자", type, 0L, currency, rate,
            null, null, null, 0, YNType.Y, null, null, null, null);
    }

    @Test
    @DisplayName("USD 환율 1,300 · 예수금 100 · 평가 10,000,000 = 10,130,000")
    void usd() {
        assertThat(asset(AssetType.INVESTMENT, "USD", new BigDecimal("1300"))
            .balanceInKrw(100L, 10_000_000L)).isEqualTo(10_130_000L);
    }

    @Test
    @DisplayName("예수금 0 · 평가금액 0 · 둘 다 0")
    void zeros() {
        Asset a = asset(AssetType.INVESTMENT, "USD", new BigDecimal("1300"));
        assertThat(a.balanceInKrw(0L, 10_000_000L)).isEqualTo(10_000_000L);
        assertThat(a.balanceInKrw(100L, 0L)).isEqualTo(130_000L);
        assertThat(a.balanceInKrw(0L, 0L)).isZero();
    }

    @Test
    @DisplayName("투자가 아닌 외화 자산(평가금액 0)은 balanceInKrw(total) 과 같다")
    void nonInvestmentSameAsOld() {
        Asset a = asset(AssetType.BANK_ACCOUNT, "USD", new BigDecimal("1400"));
        assertThat(a.balanceInKrw(3_250L, 0L)).isEqualTo(a.balanceInKrw(3_250L));
    }

    @Test
    @DisplayName("반올림 경계 — 예수금 × 환율만 HALF_UP, 음수 예수금도 대칭 없이 HALF_UP")
    void rounding() {
        Asset a = asset(AssetType.INVESTMENT, "USD", new BigDecimal("1300.5"));
        assertThat(a.balanceInKrw(3L, 7L)).isEqualTo(3_902L + 7L);   // 3901.5 → 3902
        assertThat(a.balanceInKrw(1L, 0L)).isEqualTo(1_301L);        // 1300.5 → 1301
    }

    @Test
    @DisplayName("원화 · 환율 없음(null/0 → 1) 은 그냥 더한다")
    void krwOrNoRate() {
        assertThat(asset(AssetType.INVESTMENT, "KRW", null).balanceInKrw(30_000L, 5L)).isEqualTo(30_005L);
        assertThat(asset(AssetType.INVESTMENT, "USD", BigDecimal.ZERO).balanceInKrw(100L, 5L)).isEqualTo(105L);
        assertThat(asset(AssetType.INVESTMENT, "USD", null).balanceInKrw(100L, 5L)).isEqualTo(105L);
    }
}
