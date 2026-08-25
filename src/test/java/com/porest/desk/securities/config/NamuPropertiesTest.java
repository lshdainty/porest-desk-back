package com.porest.desk.securities.config;

import com.porest.desk.securities.type.NamuEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 나무 연동 환경 설정.
 *
 * <p>여기서 지키는 건 하나다 — <b>도메인과 계좌구분이 어긋난 채로 기동되지 않는 것.</b>
 * 어긋난 조합은 기동에서 아무 티가 안 나고 잔고 조회에서만 계좌번호 오류로 터진다.
 * 실제로 그렇게 터져서 이 검사를 넣었다.
 */
class NamuPropertiesTest {

    private NamuProperties props(NamuEnvironment environment, String baseUrl) {
        NamuProperties p = new NamuProperties();
        p.setEnvironment(environment);
        p.setBaseUrl(baseUrl);
        return p;
    }

    @Test
    @DisplayName("base-url 을 비우면 환경이 도메인을 정한다 — 설정 하나로 끝난다")
    void environmentDrivesBaseUrl() {
        assertThat(props(NamuEnvironment.LIVE, null).getBaseUrl()).isEqualTo("https://api.nhplug.com:8443");
        assertThat(props(NamuEnvironment.MOCK, "  ").getBaseUrl()).isEqualTo("https://moapi.nhplug.com:8443");
    }

    @Test
    @DisplayName("토큰 발급은 환경과 무관하게 항상 운영 — 모의투자는 발급을 제공하지 않는다")
    void authUrlStaysLive() {
        assertThat(props(NamuEnvironment.MOCK, null).getAuthBaseUrl())
            .isEqualTo("https://api.nhplug.com:8443");
    }

    @Test
    @DisplayName("환경과 도메인이 어긋나면 기동을 막는다 — 운영 환경에 모의투자 도메인")
    void mismatchFailsStartup() {
        assertThatThrownBy(() -> props(NamuEnvironment.LIVE, "https://moapi.nhplug.com:8443")
                .verifyEnvironmentMatchesBaseUrl())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MOCK");
    }

    @Test
    @DisplayName("반대 방향도 막는다 — 모의투자 환경에 운영 도메인")
    void reverseMismatchFailsStartup() {
        assertThatThrownBy(() -> props(NamuEnvironment.MOCK, "https://api.nhplug.com:8443")
                .verifyEnvironmentMatchesBaseUrl())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("환경이 비면 기동을 막는다 — 도메인도 계좌구분도 정할 수 없다")
    void nullEnvironmentFailsStartup() {
        assertThatThrownBy(() -> props(null, null).verifyEnvironmentMatchesBaseUrl())
            .isInstanceOf(IllegalStateException.class);
        assertThat(props(null, null).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("짝이 맞으면 통과한다. 나무 도메인이 아니면(스텁·프록시) 경고만 하고 통과한다")
    void matchingOrUnknownHostPasses() {
        assertThatCode(() -> props(NamuEnvironment.MOCK, "https://moapi.nhplug.com:8443")
            .verifyEnvironmentMatchesBaseUrl()).doesNotThrowAnyException();
        assertThatCode(() -> props(NamuEnvironment.LIVE, "http://localhost:9999")
            .verifyEnvironmentMatchesBaseUrl()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계좌구분은 환경이 들고 있다 — 설정으로 따로 두면 어긋난다")
    void accountTypesComeFromEnvironment() {
        assertThat(NamuEnvironment.LIVE.accepts("01")).isTrue();
        assertThat(NamuEnvironment.LIVE.accepts("02")).isTrue();
        assertThat(NamuEnvironment.LIVE.accepts("03")).isFalse();
        assertThat(NamuEnvironment.MOCK.accepts("03")).isTrue();
        assertThat(NamuEnvironment.MOCK.accepts("01")).isFalse();
        // 값이 비면 어느 쪽인지 알 수 없다 — 찍지 않고 거절한다.
        assertThat(NamuEnvironment.LIVE.accepts(null)).isFalse();
        assertThat(NamuEnvironment.LIVE.accepts("")).isFalse();
    }
}
