package com.porest.desk.securities.client;

import com.porest.desk.common.config.properties.AppProperties;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Redis 토큰 캐시 — <b>Redis 에 들어가는 문자열</b>을 본다.
 *
 * <p>"암호화 코드를 넣었다" 는 검증이 아니다. 이 테스트가 고정하는 것은 두 가지다.
 *
 * <ol>
 *   <li>{@code set} 으로 넘어가는 값이 원본 토큰과 <b>다르고</b>, 복호화하면 원본이 나온다
 *       — 캡처한 인자를 직접 본다. cipher 를 목으로 두면 이 확인이 성립하지 않으므로
 *       진짜 {@link AesGcmCipher} 를 쓴다.</li>
 *   <li>평문 시절에 저장된 값이 남아 있어도 <b>깨지지 않는다</b> — 캐시 미스로 낮춰
 *       재발급으로 흘린다. 여기서 예외가 새면 잔고 조회가 통째로 죽는다.</li>
 * </ol>
 *
 * <p>TTL 도 함께 고정한다. 만료 버퍼(발급 응답 - 60초)를 계산하는 쪽은
 * {@code AbstractBrokerTokenManager} 지만, 그 결과를 Redis 에 그대로 넘기는 건 여기다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisBrokerTokenStoreTest {

    private static final SecuritiesBroker BROKER = SecuritiesBroker.NAMU;
    private static final long USER = 7L;
    private static final String KEY = "securities:token:NAMU:7";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.namu-access-token-24h.signature";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private AesGcmCipher cipher;
    private RedisBrokerTokenStore sut;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getSecurity().setEncryptionKey(Base64.getEncoder().encodeToString("porest-test-key-32-bytes-long!!!".getBytes()));
        cipher = new AesGcmCipher(props);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        sut = new RedisBrokerTokenStore(redisTemplate, cipher);
    }

    /** {@code put} 이 Redis 로 넘긴 값. */
    private String storedValue() {
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(KEY), value.capture(), any(Duration.class));
        return value.getValue();
    }

    @Nested
    @DisplayName("저장 — Redis 에 남는 문자열")
    class Storing {

        @Test
        @DisplayName("토큰 원문이 아니라 암호문이 들어간다")
        void storesCiphertext() {
            sut.put(BROKER, USER, TOKEN, 86340L);

            String stored = storedValue();
            assertThat(stored).isNotEqualTo(TOKEN);
            assertThat(stored).doesNotContain(TOKEN);
            assertThat(cipher.decrypt(stored)).isEqualTo(TOKEN);
        }

        @Test
        @DisplayName("TTL 은 받은 초를 그대로 넘긴다 — 만료 버퍼를 여기서 깎지 않는다")
        void keepsTtl() {
            sut.put(BROKER, USER, TOKEN, 86340L);

            ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
            verify(valueOps).set(eq(KEY), any(String.class), ttl.capture());
            assertThat(ttl.getValue()).isEqualTo(Duration.ofSeconds(86340L));
        }

        @Test
        @DisplayName("TTL 이 0 이하면 저장하지 않는다 — 이미 만료된 토큰이다")
        void skipsExpiredToken() {
            sut.put(BROKER, USER, TOKEN, 0L);

            then(valueOps).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("조회")
    class Reading {

        @Test
        @DisplayName("put 이 넣은 값을 get 이 원문으로 돌려준다")
        void roundTrip() {
            sut.put(BROKER, USER, TOKEN, 86340L);
            String stored = storedValue(); // verify 를 stubbing 인자 안에서 부르면 Mockito 가 꼬인다
            given(valueOps.get(KEY)).willReturn(stored);

            assertThat(sut.get(BROKER, USER)).contains(TOKEN);
        }

        @Test
        @DisplayName("값이 없으면 빈 결과")
        void miss() {
            given(valueOps.get(KEY)).willReturn(null);

            assertThat(sut.get(BROKER, USER)).isEmpty();
            verify(redisTemplate, never()).delete(KEY);
        }

        @Test
        @DisplayName("평문 시절 값이 남아 있어도 터지지 않는다 — 캐시 미스로 낮춰 재발급을 태운다")
        void legacyPlaintextIsTreatedAsMiss() {
            given(valueOps.get(KEY)).willReturn(TOKEN); // 암호화 전에 저장된 값

            assertThat(sut.get(BROKER, USER)).isEmpty();
            verify(redisTemplate).delete(KEY); // 다음 put 이 덮기 전까지 쓰레기를 들고 있지 않는다
        }

        @Test
        @DisplayName("다른 키로 암호화된 값도 같은 경로로 복구된다 — 키 교체")
        void tokenFromAnotherKeyIsTreatedAsMiss() {
            AppProperties other = new AppProperties();
            other.getSecurity().setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
            given(valueOps.get(KEY)).willReturn(new AesGcmCipher(other).encrypt(TOKEN));

            assertThat(sut.get(BROKER, USER)).isEmpty();
            verify(redisTemplate).delete(KEY);
        }
    }

    @Test
    @DisplayName("evict 는 키를 지운다")
    void evict() {
        sut.evict(BROKER, USER);

        verify(redisTemplate).delete(KEY);
    }
}
