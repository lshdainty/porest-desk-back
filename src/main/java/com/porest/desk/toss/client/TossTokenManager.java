package com.porest.desk.toss.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.toss.client.dto.TossTokenResponse;
import com.porest.desk.toss.config.TossProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 토스증권 OAuth2 액세스 토큰 관리자.<br>
 * Client Credentials Grant 로 토큰을 발급하고 만료 전까지 인메모리로 캐싱한다.
 *
 * <p>토스증권 정책상 <b>client 당 유효한 access token 은 1 개</b>이며 재발급 시 이전 토큰은 즉시
 * 무효화된다. refresh token 은 제공되지 않으므로 만료 시 동일 엔드포인트로 재발급한다.</p>
 *
 * <p>토큰 문자열과 만료 시각은 항상 정합한 '쌍'으로 보여야 하므로 두 값을 불변 {@link TokenSnapshot}
 * 으로 묶어 단일 volatile 참조로 원자적으로 게시한다(두 개의 독립 volatile 은 쌍의 원자성을 보장하지 못함).</p>
 *
 * <p><b>확장 메모</b>: 현재는 단일 인스턴스 기준 인메모리 캐시다. 다중 인스턴스로 배포하면 각
 * 인스턴스가 독립적으로 토큰을 발급해 서로를 무효화시킬 수 있으므로, 그 시점에는 토큰을
 * Redis 등 공유 스토어로 옮기고 발급 경합을 분산락으로 보호해야 한다(인프라에 Redis 가 이미 있음).</p>
 */
@Slf4j
@Component
public class TossTokenManager {

    /** 만료 시각 안전 버퍼(초). 실제 만료보다 이만큼 일찍 재발급한다. */
    private static final long EXPIRY_BUFFER_SECONDS = 60L;

    /** 토큰 문자열과 만료 시각(epoch millis, 버퍼 반영)을 한 쌍으로 묶은 불변 스냅샷. */
    private record TokenSnapshot(String token, long expiresAtMillis) {
    }

    private final RestTemplate tossRestTemplate;
    private final TossProperties tossProperties;

    /** 현재 토큰 스냅샷 (없으면 null). 단일 volatile 참조로 원자적 publish. */
    private volatile TokenSnapshot snapshot;

    public TossTokenManager(@Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
                            TossProperties tossProperties) {
        this.tossRestTemplate = tossRestTemplate;
        this.tossProperties = tossProperties;
    }

    /**
     * 유효한 access token 을 반환한다. 캐시가 비었거나 만료가 임박하면 새로 발급한다.
     */
    public String getAccessToken() {
        TokenSnapshot current = snapshot;
        if (isValid(current)) {
            return current.token();
        }
        return issueToken();
    }

    /**
     * 캐시된 토큰을 강제로 폐기한다. (예: 401 응답으로 토큰 무효가 확인된 경우)
     */
    public void invalidate() {
        this.snapshot = null;
    }

    private static boolean isValid(TokenSnapshot s) {
        return s != null && System.currentTimeMillis() < s.expiresAtMillis();
    }

    /**
     * OAuth2 Client Credentials Grant 로 새 토큰을 발급하고 캐시에 저장한다.
     * 동시 호출 시 한 번만 발급되도록 동기화한다(double-checked).
     */
    private synchronized String issueToken() {
        // 락 획득 사이 다른 스레드가 이미 발급했을 수 있다.
        TokenSnapshot current = snapshot;
        if (isValid(current)) {
            return current.token();
        }
        if (!tossProperties.isConfigured()) {
            log.warn("토스증권 연동 설정(app.toss.base-url/client-id/client-secret)이 비어 있어 토큰을 발급할 수 없습니다");
            throw new ExternalServiceException(DeskErrorCode.TOSS_NOT_CONFIGURED);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", tossProperties.getClientId());
        form.add("client_secret", tossProperties.getClientSecret());

        try {
            TossTokenResponse res = tossRestTemplate.postForObject(
                    "/oauth2/token", new HttpEntity<>(form, headers), TossTokenResponse.class);
            if (res == null || res.accessToken() == null) {
                throw new ExternalServiceException(DeskErrorCode.TOSS_AUTH_ERROR);
            }
            this.snapshot = new TokenSnapshot(res.accessToken(), computeExpiresAt(res.expiresIn()));
            log.debug("토스증권 액세스 토큰 발급 완료 (expiresIn={}s)", res.expiresIn());
            return res.accessToken();
        } catch (RestClientException e) {
            invalidate();
            log.error("토스증권 토큰 발급 실패", e);
            throw new ExternalServiceException(DeskErrorCode.TOSS_AUTH_ERROR, e);
        }
    }

    /**
     * 만료 시각을 계산한다. 외부(토스 API) 응답값을 신뢰하므로 곱셈/덧셈 오버플로를
     * saturating 처리해 음수 만료(=재발급 storm)를 방지한다.
     */
    private static long computeExpiresAt(long expiresInSeconds) {
        long ttlSeconds = Math.max(0L, expiresInSeconds - EXPIRY_BUFFER_SECONDS);
        long ttlMillis = ttlSeconds > (Long.MAX_VALUE / 1000L) ? Long.MAX_VALUE : ttlSeconds * 1000L;
        try {
            return Math.addExact(System.currentTimeMillis(), ttlMillis);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
