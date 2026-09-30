package com.porest.desk.apitoken.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final ApiTokenRateLimiter limiter = new ApiTokenRateLimiter(now::get);

    private int acquired(Long tokenRowId, int attempts) {
        int ok = 0;
        for (int i = 0; i < attempts; i++) {
            if (limiter.tryAcquire(tokenRowId)) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    @DisplayName("같은 1초 안에서는 한도까지만 받는다")
    void limitsWithinOneSecond() {
        assertThat(acquired(1L, 20)).isEqualTo(ApiTokenRateLimiter.LIMIT_PER_SECOND);
    }

    @Test
    @DisplayName("다음 초가 되면 다시 받는다 — 막힌 토큰이 영영 막혀 있으면 안 된다")
    void resetsNextSecond() {
        acquired(1L, 20);

        now.addAndGet(1_000L);

        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    @Test
    @DisplayName("한도는 토큰마다 따로다 — 한 사람의 폭주가 다른 사람을 막지 않는다")
    void countsPerToken() {
        acquired(1L, 20);

        assertThat(acquired(2L, 20)).isEqualTo(ApiTokenRateLimiter.LIMIT_PER_SECOND);
    }

    @Test
    @DisplayName("기본 생성자는 실제 시계를 쓴다 — 첫 호출은 받는다")
    void defaultClock() {
        assertThat(new ApiTokenRateLimiter().tryAcquire(1L)).isTrue();
    }
}
