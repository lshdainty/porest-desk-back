package com.porest.desk.securities.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 증권사 인증의 공통 부분 — 캐시·복호화·만료버퍼·예외변환이 자식과 무관하게 도는지 본다.
 *
 * <p>증권사별 프로토콜은 {@code issueToken}/{@code applyAuth} 훅 안에만 있어야 한다.
 * 훅을 재정의하지 않은 자식이 Bearer 만 받고, 재정의한 자식(나무)이 헤더를 더 붙이는지도 함께 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerTokenManagerTest {

    private static final long USER = 7L;

    @Mock private UserSecuritiesCredentialRepository credentialRepository;
    @Mock private AesGcmCipher cipher;

    private BrokerTokenStore store;
    private AtomicInteger issueCount;

    /** 훅만 채운 테스트용 자식 — 부모가 실제로 무슨 일을 하는지 드러낸다. */
    private class StubManager extends AbstractBrokerTokenManager {
        private final BrokerToken token;
        private final RuntimeException failure;

        StubManager(BrokerToken token, RuntimeException failure) {
            super(credentialRepository, cipher, store);
            this.token = token;
            this.failure = failure;
        }

        @Override
        public SecuritiesBroker broker() {
            return SecuritiesBroker.TOSS;
        }

        @Override
        protected BrokerToken issueToken(String apiKey, String apiSecret) {
            issueCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return token;
        }
    }

    @BeforeEach
    void setUp() {
        store = new InMemoryBrokerTokenStore();
        issueCount = new AtomicInteger();
        given(cipher.decrypt("keyEnc")).willReturn("key");
        given(cipher.decrypt("secretEnc")).willReturn("secret");
    }

    private void credentialExists() {
        UserSecuritiesCredential cred = UserSecuritiesCredential.verified(
            USER, SecuritiesBroker.TOSS, "keyEnc", "secretEnc", LocalDateTime.now());
        given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
            USER, SecuritiesBroker.TOSS, YNType.N, YNType.Y)).willReturn(Optional.of(cred));
    }

    @Nested
    @DisplayName("토큰 캐시")
    class Caching {

        @Test
        @DisplayName("두 번째 호출은 발급하지 않는다 — 나무는 재발급이 사용자 보안 알림으로 쌓인다")
        void secondCallHitsCache() {
            credentialExists();
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("tok", 3600L), null);

            assertThat(sut.getAccessToken(USER)).isEqualTo("tok");
            assertThat(sut.getAccessToken(USER)).isEqualTo("tok");

            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("만료 버퍼보다 짧게 남은 토큰은 캐시하지 않는다 — 만료 직전 토큰으로 나가면 401 이다")
        void doesNotCacheAlreadyWithinBuffer() {
            credentialExists();
            AbstractBrokerTokenManager sut =
                new StubManager(new BrokerToken("tok", AbstractBrokerTokenManager.EXPIRY_BUFFER_SECONDS), null);

            sut.getAccessToken(USER);
            sut.getAccessToken(USER);

            assertThat(issueCount).hasValue(2);
        }

        @Test
        @DisplayName("무효화하면 다시 발급한다")
        void invalidateForcesReissue() {
            credentialExists();
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("tok", 3600L), null);

            sut.getAccessToken(USER);
            sut.invalidate(USER);
            sut.getAccessToken(USER);

            assertThat(issueCount).hasValue(2);
        }
    }

    @Nested
    @DisplayName("실패 처리")
    class Failures {

        @Test
        @DisplayName("크리덴셜 미등록은 SECURITIES_CREDENTIAL_REQUIRED — 발급 시도조차 하지 않는다")
        void missingCredential() {
            given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
                USER, SecuritiesBroker.TOSS, YNType.N, YNType.Y)).willReturn(Optional.empty());
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("tok", 3600L), null);

            assertThatThrownBy(() -> sut.getAccessToken(USER))
                .isInstanceOf(ExternalServiceException.class);
            assertThat(issueCount).hasValue(0);
        }

        @Test
        @DisplayName("발급 중 통신 오류는 SECURITIES_AUTH_ERROR 로 바뀐다 — 원본 예외가 그대로 새지 않는다")
        void restClientExceptionIsTranslated() {
            credentialExists();
            AbstractBrokerTokenManager sut = new StubManager(null, new RestClientException("boom"));

            assertThatThrownBy(() -> sut.getAccessToken(USER))
                .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("검증은 토큰이 비어 있어도 실패로 본다 — 200 인데 본문이 빈 경우가 있다")
        void verifyRejectsBlankToken() {
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("  ", 3600L), null);

            assertThatThrownBy(() -> sut.verify("key", "secret"))
                .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("검증은 크리덴셜 저장 없이 키만으로 돈다 — 등록 전에 부르기 때문")
        void verifyDoesNotTouchRepository() {
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("tok", 3600L), null);

            sut.verify("key", "secret");

            assertThat(issueCount).hasValue(1);
        }
    }

    @Nested
    @DisplayName("인증 헤더 — 훅을 재정의한 증권사만 달라진다")
    class AuthHeaders {

        @Test
        @DisplayName("기본은 Bearer 하나 (토스)")
        void defaultIsBearerOnly() {
            credentialExists();
            AbstractBrokerTokenManager sut = new StubManager(new BrokerToken("tok", 3600L), null);

            HttpHeaders headers = sut.authHeaders(USER);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok");
            assertThat(headers.headerNames()).containsExactly(HttpHeaders.AUTHORIZATION);
        }

        @Test
        @DisplayName("나무는 평문 키/시크릿을 매 호출에 함께 싣는다 — applyAuth 재정의")
        void namuAddsClientHeaders() {
            UserSecuritiesCredential cred = UserSecuritiesCredential.verified(
                USER, SecuritiesBroker.NAMU, "keyEnc", "secretEnc", LocalDateTime.now());
            given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
                USER, SecuritiesBroker.NAMU, YNType.N, YNType.Y)).willReturn(Optional.of(cred));
            store.put(SecuritiesBroker.NAMU, USER, "tok", 3600L);

            NamuTokenManager sut = new NamuTokenManager(credentialRepository, cipher, store, null);
            HttpHeaders headers = sut.authHeaders(USER);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok");
            assertThat(headers.getFirst("x-client-id")).isEqualTo("key");
            assertThat(headers.getFirst("x-client-secret")).isEqualTo("secret");
        }
    }
}
