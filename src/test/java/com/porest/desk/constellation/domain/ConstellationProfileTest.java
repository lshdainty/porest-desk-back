package com.porest.desk.constellation.domain;

import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보호권(구름 가림) 충전/소비 규칙 — 수집 7일마다 +1, 최대 보유 2, 캡 초과 충전분 소멸.
 */
class ConstellationProfileTest {

    private ConstellationProfile profile() {
        User user = User.createUser(null, "tester", "테스터", "tester@porest.com");
        return ConstellationProfile.createProfile(user);
    }

    @Test
    @DisplayName("recordGrown 7회 누적 → 보호권 +1, 진행도 리셋")
    void chargeGuardEverySevenGrown() {
        ConstellationProfile profile = profile();

        for (int i = 0; i < 6; i++) {
            profile.recordGrown();
        }
        assertThat(profile.getGuardCount()).isZero();
        assertThat(profile.getGrownSinceCharge()).isEqualTo(6);

        profile.recordGrown(); // 7회째
        assertThat(profile.getGuardCount()).isEqualTo(1);
        assertThat(profile.getGrownSinceCharge()).isZero();
    }

    @Test
    @DisplayName("보호권 최대 2 — 캡 상태에서 7일 충전분은 소멸(진행도만 리셋)")
    void guardCapAtTwo() {
        ConstellationProfile profile = profile();
        for (int i = 0; i < 21; i++) { // 7 × 3
            profile.recordGrown();
        }
        assertThat(profile.getGuardCount()).isEqualTo(2); // 3번째 충전은 캡으로 소멸
        assertThat(profile.getGrownSinceCharge()).isZero();
    }

    @Test
    @DisplayName("consumeGuards — 보유분 차감, 부족하면 예외")
    void consumeGuards() {
        ConstellationProfile profile = profile();
        for (int i = 0; i < 14; i++) {
            profile.recordGrown();
        }
        assertThat(profile.getGuardCount()).isEqualTo(2);
        assertThat(profile.canConsume(2)).isTrue();

        profile.consumeGuards(2);
        assertThat(profile.getGuardCount()).isZero();
        assertThat(profile.canConsume(1)).isFalse();
        assertThatThrownBy(() -> profile.consumeGuards(1))
            .isInstanceOf(IllegalStateException.class);
    }
}
