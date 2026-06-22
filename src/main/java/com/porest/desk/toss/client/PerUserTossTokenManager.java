package com.porest.desk.toss.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.toss.client.dto.TossTokenResponse;
import com.porest.desk.toss.credential.domain.UserTossCredential;
import com.porest.desk.toss.credential.repository.UserTossCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 토스 액세스 토큰 관리자(데이터격리). userRowId 별로 본인 크리덴셜로 발급한 토큰을 캐시한다.
 * 토스 API는 시세·계좌 구분 없이 발급된 토큰으로만 호출하므로(scope 없음), 모든 조회가 이 경로를 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerUserTossTokenManager {

    private static final long EXPIRY_BUFFER_SECONDS = 60L;

    private record TokenSnapshot(String token, long expiresAtMillis) {
    }

    private final UserTossCredentialRepository credentialRepository;
    private final AesGcmCipher cipher;
    private final TossTokenIssuer issuer;

    private final Map<Long, TokenSnapshot> cache = new ConcurrentHashMap<>();

    /** 사용자 본인 키로 발급한 유효 토큰. 크리덴셜 미등록 시 {@code TOSS_CREDENTIAL_REQUIRED}. */
    public String getAccessToken(Long userRowId) {
        TokenSnapshot s = cache.get(userRowId);
        if (s != null && System.currentTimeMillis() < s.expiresAtMillis()) {
            return s.token();
        }
        return issueFor(userRowId);
    }

    public void invalidate(Long userRowId) {
        cache.remove(userRowId);
    }

    private synchronized String issueFor(Long userRowId) {
        TokenSnapshot current = cache.get(userRowId);
        if (current != null && System.currentTimeMillis() < current.expiresAtMillis()) {
            return current.token();
        }
        UserTossCredential cred = credentialRepository
            .findByUserRowIdAndIsDeletedAndIsVerified(userRowId, YNType.N, YNType.Y)
            .orElseThrow(() -> new ExternalServiceException(DeskErrorCode.TOSS_CREDENTIAL_REQUIRED));

        String clientId = cipher.decrypt(cred.getClientIdEnc());
        String clientSecret = cipher.decrypt(cred.getClientSecretEnc());
        try {
            TossTokenResponse res = issuer.issue(clientId, clientSecret);
            if (res == null || res.accessToken() == null) {
                throw new ExternalServiceException(DeskErrorCode.TOSS_AUTH_ERROR);
            }
            cache.put(userRowId, new TokenSnapshot(res.accessToken(), computeExpiresAt(res.expiresIn())));
            log.debug("토스증권 사용자 토큰 발급 완료 (userRowId={}, expiresIn={}s)", userRowId, res.expiresIn());
            return res.accessToken();
        } catch (RestClientException e) {
            invalidate(userRowId);
            log.error("토스증권 사용자 토큰 발급 실패 (userRowId={})", userRowId, e);
            throw new ExternalServiceException(DeskErrorCode.TOSS_AUTH_ERROR, e);
        }
    }

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
