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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 증권사 인증의 공통 부분 — 캐시·복호화·만료버퍼·발급 직렬화·예외변환이 자식과 무관하게 도는지 본다.
 *
 * <p>증권사별 프로토콜은 {@code issueToken}/{@code applyAuth} 훅 안에만 있어야 한다.
 * 훅을 재정의하지 않은 자식이 Bearer 만 받고, 재정의한 자식(나무)이 헤더를 더 붙이는지도 함께 확인한다.
 *
 * <p><b>이 테스트가 지키는 것은 "발급 횟수" 다.</b> 나무는 발급 한 번이 사용자 알림톡 한 건이라
 * 횟수 자체가 계약이다. {@code issueCount} 를 고정하지 않으면 다음 리팩터링에서 조용히 늘어난다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerTokenManagerTest {

    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;
    private static final String API_KEY = "APPKEY-AAA";
    private static final String API_SECRET = "S3CRET-VALUE";

    @Mock private UserSecuritiesCredentialRepository credentialRepository;
    @Mock private AesGcmCipher cipher;

    private BrokerTokenStore store;
    private AtomicInteger issueCount;

    /** 훅만 채운 테스트용 자식 — 부모가 실제로 무슨 일을 하는지 드러낸다. */
    private class StubManager extends AbstractBrokerTokenManager {
        private final Supplier<BrokerToken> issuer;
        private long cooldownMillis = REISSUE_COOLDOWN_SECONDS * 1000L;

        StubManager(Supplier<BrokerToken> issuer) {
            super(credentialRepository, cipher, store);
            this.issuer = issuer;
        }

        StubManager withCooldown(long millis) {
            this.cooldownMillis = millis;
            return this;
        }

        @Override
        protected long reissueCooldownMillis() {
            return cooldownMillis;
        }

        @Override
        public SecuritiesBroker broker() {
            return SecuritiesBroker.TOSS;
        }

        @Override
        protected BrokerToken issueToken(String apiKey, String apiSecret) {
            issueCount.incrementAndGet();
            return issuer.get();
        }
    }

    private StubManager manager(BrokerToken token) {
        return new StubManager(() -> token);
    }

    private StubManager failing(RuntimeException failure) {
        return new StubManager(() -> {
            throw failure;
        });
    }

    @BeforeEach
    void setUp() {
        store = new InMemoryBrokerTokenStore();
        issueCount = new AtomicInteger();
        given(cipher.decrypt("keyEnc")).willReturn(API_KEY);
        given(cipher.decrypt("secretEnc")).willReturn(API_SECRET);
    }

    private void credentialExists() {
        credentialExists(USER, "keyEnc", "secretEnc");
    }

    private void credentialExists(long userRowId, String keyEnc, String secretEnc) {
        UserSecuritiesCredential cred = UserSecuritiesCredential.verified(
            userRowId, SecuritiesBroker.TOSS, keyEnc, secretEnc, LocalDateTime.now());
        given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
            userRowId, SecuritiesBroker.TOSS, YNType.N, YNType.Y)).willReturn(Optional.of(cred));
    }

    @Nested
    @DisplayName("토큰 캐시 — 발급 횟수가 계약이다")
    class Caching {

        @Test
        @DisplayName("두 번째 호출은 발급하지 않는다 — 나무는 재발급이 사용자 보안 알림으로 쌓인다")
        void secondCallHitsCache() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 3600L));

            assertThat(sut.getAccessToken(USER)).isEqualTo("tok");
            assertThat(sut.getAccessToken(USER)).isEqualTo("tok");

            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("캐시가 살아 있으면 연속 20번을 불러도 발급은 0회다")
        void repeatedCallsDoNotReissue() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 86400L));
            sut.getAccessToken(USER);
            issueCount.set(0);

            for (int i = 0; i < 20; i++) {
                assertThat(sut.getAccessToken(USER)).isEqualTo("tok");
            }

            assertThat(issueCount).hasValue(0);
        }

        @Test
        @DisplayName("만료 버퍼보다 짧게 남은 토큰은 캐시하지 않는다 — 만료 직전 토큰으로 나가면 401 이다")
        void doesNotCacheAlreadyWithinBuffer() {
            credentialExists();
            AbstractBrokerTokenManager sut =
                manager(new BrokerToken("tok", AbstractBrokerTokenManager.EXPIRY_BUFFER_SECONDS));

            sut.getAccessToken(USER);
            sut.getAccessToken(USER);

            assertThat(issueCount).hasValue(2);
        }

        @Test
        @DisplayName("만료 60초 전에 갱신한다 — TTL 이 만료-버퍼로 잡힌다")
        void cachesWithExpiryBuffer() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 61L));

            sut.getAccessToken(USER);

            // 61 - 60 = 1초만 유효하다. 그 사이엔 캐시 히트다.
            assertThat(store.get(SecuritiesBroker.TOSS, USER)).contains("tok");
        }

        @Test
        @DisplayName("무효화하면 다시 발급한다 — 연결 해제처럼 우리가 확실히 아는 자리")
        void invalidateForcesReissue() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 3600L));

            sut.getAccessToken(USER);
            sut.invalidate(USER);
            sut.getAccessToken(USER);

            assertThat(issueCount).hasValue(2);
        }
    }

    @Nested
    @DisplayName("실패 처리 — 토큰 문제가 아닌 실패에는 토큰을 버리지 않는다")
    class Failures {

        @Test
        @DisplayName("크리덴셜 미등록은 SECURITIES_CREDENTIAL_REQUIRED — 발급 시도조차 하지 않는다")
        void missingCredential() {
            given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
                USER, SecuritiesBroker.TOSS, YNType.N, YNType.Y)).willReturn(Optional.empty());
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 3600L));

            assertThatThrownBy(() -> sut.getAccessToken(USER))
                .isInstanceOf(ExternalServiceException.class);
            assertThat(issueCount).hasValue(0);
        }

        @Test
        @DisplayName("발급 중 통신 오류는 SECURITIES_AUTH_ERROR 로 바뀐다 — 원본 예외가 그대로 새지 않는다")
        void restClientExceptionIsTranslated() {
            credentialExists();
            AbstractBrokerTokenManager sut = failing(new RestClientException("boom"));

            assertThatThrownBy(() -> sut.getAccessToken(USER))
                .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("발급 타임아웃이 남의 스레드가 방금 저장한 토큰을 지우지 않는다")
        void issueFailureDoesNotEvictExistingToken() {
            credentialExists();
            // 발급 도중 다른 스레드가 먼저 발급을 끝내 캐시에 넣은 상황을 그대로 재현한다.
            // 예전에는 이 catch 에서 invalidate 를 불러 멀쩡한 토큰이 날아갔고,
            // 다음 호출이 또 발급을 부르며 알림톡이 한 건 더 나갔다.
            AbstractBrokerTokenManager sut = new StubManager(() -> {
                store.put(SecuritiesBroker.TOSS, USER, "other-thread-token", 3600L);
                throw new ResourceAccessException("I/O error on POST request");
            });

            assertThatThrownBy(() -> sut.getAccessToken(USER))
                .isInstanceOf(ExternalServiceException.class);

            assertThat(store.get(SecuritiesBroker.TOSS, USER)).contains("other-thread-token");
        }

        @Test
        @DisplayName("발급 실패 예외에 앱키·시크릿이 남지 않는다 — 발급 URL 이 쿼리에 시크릿을 싣는다")
        void failureMessageHasNoSecrets() {
            credentialExists();
            String leakyUrl = "https://api.nhplug.com:8443/oauth2/token?grant_type=client_credentials"
                + "&scope=oob&appkey=" + API_KEY + "&appsecretkey=" + API_SECRET;
            AbstractBrokerTokenManager sut =
                failing(new ResourceAccessException("I/O error on POST request for \"" + leakyUrl + "\""));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> sut.getAccessToken(USER));

            assertThat(thrown).isInstanceOf(ExternalServiceException.class);
            String chain = stackTraceOf(thrown);
            assertThat(chain).doesNotContain(API_SECRET).doesNotContain(API_KEY);
            assertThat(chain).contains("***");
        }

        private static String stackTraceOf(Throwable t) {
            java.io.StringWriter w = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(w));
            return w.toString();
        }
    }

    @Nested
    @DisplayName("401 재발급 — 쿨다운이 증폭을 막는다")
    class UnauthorizedHandling {

        @Test
        @DisplayName("방금 발급한 토큰으로 401 이면 버리지 않는다 — 종목 수만큼 재발급되던 자리")
        void refusesReissueWithinCooldown() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 86400L));
            sut.getAccessToken(USER);

            assertThat(sut.invalidateOnUnauthorized(USER)).isFalse();

            assertThat(store.get(SecuritiesBroker.TOSS, USER)).contains("tok");
            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("시세 루프처럼 401 이 계속 와도 폴링 한 바퀴에 발급은 1회다")
        void repeatedUnauthorizedIssuesOnlyOnce() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 86400L));

            // 종목 30개가 차례로 401 을 맞는 상황: 첫 건만 재발급하고 나머지는 거절된다.
            int reissued = 0;
            for (int i = 0; i < 30; i++) {
                if (sut.invalidateOnUnauthorized(USER)) {
                    sut.getAccessToken(USER);
                    reissued++;
                }
            }

            assertThat(reissued).isEqualTo(1);
            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("쿨다운이 지난 뒤 401 이면 버리고 재발급한다 — 진짜 폐기된 토큰은 갱신돼야 한다")
        void reissuesAfterCooldown() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 86400L)).withCooldown(0L);
            sut.getAccessToken(USER);

            assertThat(sut.invalidateOnUnauthorized(USER)).isTrue();
            assertThat(store.get(SecuritiesBroker.TOSS, USER)).isEmpty();

            sut.getAccessToken(USER);
            assertThat(issueCount).hasValue(2);
        }

        @Test
        @DisplayName("이 인스턴스가 발급한 적 없는 토큰(다른 인스턴스가 넣은 것)은 401 이면 버린다")
        void evictsTokenThisInstanceNeverIssued() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("new", 86400L));
            store.put(SecuritiesBroker.TOSS, USER, "from-other-instance", 86400L);

            assertThat(sut.invalidateOnUnauthorized(USER)).isTrue();
            assertThat(store.get(SecuritiesBroker.TOSS, USER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시 발급 — 사용자당 1회, 사용자끼리는 막지 않는다")
    class Concurrency {

        @Test
        @DisplayName("캐시가 빈 채로 동시 요청 16건이 와도 발급은 1회다")
        void concurrentColdStartIssuesOnce() throws Exception {
            credentialExists();
            AbstractBrokerTokenManager sut = new StubManager(() -> {
                sleep(30);
                return new BrokerToken("tok", 86400L);
            });

            int threads = 16;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<String>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return sut.getAccessToken(USER);
                    }));
                }
                start.countDown();
                for (Future<String> f : futures) {
                    assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo("tok");
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("한 사용자의 발급이 다른 사용자를 막지 않는다 — 락은 사용자별이다")
        void issuingForOneUserDoesNotBlockAnother() throws Exception {
            credentialExists();
            credentialExists(OTHER_USER, "keyEnc2", "secretEnc2");
            given(cipher.decrypt("keyEnc2")).willReturn("APPKEY-BBB");
            given(cipher.decrypt("secretEnc2")).willReturn("S3CRET-BBB");

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AbstractBrokerTokenManager sut = new StubManager(() -> {
                throw new IllegalStateException("사용되지 않음");
            }) {
                @Override
                protected BrokerToken issueToken(String apiKey, String apiSecret) {
                    if (API_KEY.equals(apiKey)) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return new BrokerToken("tok-a", 86400L);
                    }
                    return new BrokerToken("tok-b", 86400L);
                }
            };

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> a = pool.submit(() -> sut.getAccessToken(USER));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

                Future<String> b = pool.submit(() -> sut.getAccessToken(OTHER_USER));
                // 전체 락이었다면 여기서 타임아웃이 난다.
                assertThat(b.get(3, TimeUnit.SECONDS)).isEqualTo("tok-b");

                release.countDown();
                assertThat(a.get(5, TimeUnit.SECONDS)).isEqualTo("tok-a");
            } finally {
                pool.shutdownNow();
            }
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("등록 검증 — 검증 발급을 버리지 않는다")
    class VerifyAndCache {

        @Test
        @DisplayName("검증에 쓴 토큰이 곧 사용 토큰이다 — 등록 1회에 발급 1회")
        void verifiedTokenIsCached() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 86400L));

            sut.verifyAndCache(USER, API_KEY, API_SECRET);
            assertThat(sut.getAccessToken(USER)).isEqualTo("tok");

            assertThat(issueCount).hasValue(1);
        }

        @Test
        @DisplayName("검증은 토큰이 비어 있어도 실패로 본다 — 200 인데 본문이 빈 경우가 있다")
        void verifyRejectsBlankToken() {
            AbstractBrokerTokenManager sut = manager(new BrokerToken("  ", 3600L));

            assertThatThrownBy(() -> sut.verifyAndCache(USER, API_KEY, API_SECRET))
                .isInstanceOf(ExternalServiceException.class);
            assertThat(store.get(SecuritiesBroker.TOSS, USER)).isEmpty();
        }

        @Test
        @DisplayName("검증은 크리덴셜 조회 없이 키만으로 돈다 — 등록 전에 부르기 때문")
        void verifyDoesNotTouchRepository() {
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 3600L));

            sut.verifyAndCache(USER, API_KEY, API_SECRET);

            assertThat(issueCount).hasValue(1);
            org.mockito.Mockito.verifyNoInteractions(credentialRepository);
        }
    }

    @Nested
    @DisplayName("인증 헤더 — 훅을 재정의한 증권사만 달라진다")
    class AuthHeaders {

        @Test
        @DisplayName("기본은 Bearer 하나 (토스)")
        void defaultIsBearerOnly() {
            credentialExists();
            AbstractBrokerTokenManager sut = manager(new BrokerToken("tok", 3600L));

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
            assertThat(headers.getFirst("x-client-id")).isEqualTo(API_KEY);
            assertThat(headers.getFirst("x-client-secret")).isEqualTo(API_SECRET);
        }
    }
}
