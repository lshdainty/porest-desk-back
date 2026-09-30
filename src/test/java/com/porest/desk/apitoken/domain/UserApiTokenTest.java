package com.porest.desk.apitoken.domain;

import com.porest.core.type.YNType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiTokenTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 30, 12, 0, 0);

    private UserApiToken token() {
        return UserApiToken.issue(1L, "보고서", "hash", "pdk_abcdefgh");
    }

    @Test
    @DisplayName("발급하면 살아 있고, 아직 쓴 적이 없다")
    void issue() {
        UserApiToken token = token();

        assertThat(token.isActive()).isTrue();
        assertThat(token.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(token.getLastUsedAt()).isNull();
    }

    @Test
    @DisplayName("폐기는 소프트 삭제다")
    void revoke() {
        UserApiToken token = token();

        token.revoke();

        assertThat(token.isActive()).isFalse();
        assertThat(token.getIsDeleted()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("처음 쓰면 시각을 남긴다")
    void touchFirstUse() {
        UserApiToken token = token();

        assertThat(token.touch(T0, MINUTE)).isTrue();
        assertThat(token.getLastUsedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("간격 안에 또 쓰면 시각을 다시 쓰지 않는다 — 조회마다 UPDATE 가 나가면 안 된다")
    void touchWithinInterval() {
        UserApiToken token = token();
        token.touch(T0, MINUTE);

        assertThat(token.touch(T0.plusSeconds(59), MINUTE)).isFalse();
        assertThat(token.getLastUsedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("간격이 지나면 다시 남긴다")
    void touchAfterInterval() {
        UserApiToken token = token();
        token.touch(T0, MINUTE);

        assertThat(token.touch(T0.plusSeconds(60), MINUTE)).isTrue();
        assertThat(token.getLastUsedAt()).isEqualTo(T0.plusSeconds(60));
    }
}
