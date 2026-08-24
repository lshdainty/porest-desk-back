package com.porest.desk.securities.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;

/**
 * 증권사 인증의 공통 부분. 증권사별 구현이 이걸 상속해 쓴다.
 *
 * <p>여기 있는 것 — 크리덴셜 조회·복호화, 토큰 캐시 조회/저장/무효화, 만료 버퍼,
 * {@link RestClientException} → {@link ExternalServiceException} 변환. 증권사가 달라도
 * 같은 부분이라 한 벌만 둔다.
 *
 * <p>자식이 재정의하는 것은 <b>{@link #issueToken}</b> 하나다. 인증 헤더가 Bearer 하나로
 * 끝나지 않는 증권사만 {@link #applyAuth} 를 추가로 재정의한다 — 나머지는 부모 것을 그대로
 * 쓰므로 기존 파일을 손대지 않는다.
 *
 * <p>평문 키/시크릿은 이 클래스 안에서만 산다. 캐시에는 토큰만 넣는다 — 평문을 장수 맵에
 * 들고 있으면 힙 덤프 한 번에 전 사용자 키가 새어 나간다.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBrokerTokenManager implements BrokerTokenManager {

    /**
     * 만료 직전 토큰으로 호출이 나가 401 을 맞는 걸 막는 여유.
     * 증권사 시계와 우리 시계가 몇 초 어긋나도 견딘다.
     */
    protected static final long EXPIRY_BUFFER_SECONDS = 60L;

    private final UserSecuritiesCredentialRepository credentialRepository;
    private final AesGcmCipher cipher;
    private final BrokerTokenStore tokenStore;

    /** 복호화한 평문 한 쌍. 이 클래스 밖으로 나가지 않는다. */
    protected record ApiCredential(String apiKey, String apiSecret) {
    }

    // ── 증권사별로 다른 것 ────────────────────────────────────────────────

    /**
     * 이 증권사의 토큰 발급 프로토콜. 실패는 {@link RestClientException} 으로 던지면
     * 부모가 {@code SECURITIES_AUTH_ERROR} 로 바꾼다.
     */
    protected abstract BrokerToken issueToken(String apiKey, String apiSecret);

    /**
     * 인증 헤더 구성. 기본은 {@code Authorization: Bearer} 하나 — 대부분 이걸로 끝난다.
     * 나무처럼 평문 키/시크릿을 매 호출 요구하는 증권사만 재정의한다.
     */
    protected void applyAuth(HttpHeaders headers, String accessToken, String apiKey, String apiSecret) {
        headers.setBearerAuth(accessToken);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────

    @Override
    public String getAccessToken(Long userRowId) {
        return tokenStore.get(broker(), userRowId).orElseGet(() -> issueFor(userRowId, activeCredential(userRowId)));
    }

    @Override
    public HttpHeaders authHeaders(Long userRowId) {
        ApiCredential cred = activeCredential(userRowId);
        String token = tokenStore.get(broker(), userRowId).orElseGet(() -> issueFor(userRowId, cred));
        HttpHeaders headers = new HttpHeaders();
        applyAuth(headers, token, cred.apiKey(), cred.apiSecret());
        return headers;
    }

    @Override
    public void verify(String apiKey, String apiSecret) {
        try {
            BrokerToken token = issueToken(apiKey, apiSecret);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_INVALID);
            }
        } catch (RestClientException e) {
            log.warn("{} 크리덴셜 검증 실패", broker(), e);
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_INVALID, e);
        }
    }

    @Override
    public void invalidate(Long userRowId) {
        tokenStore.evict(broker(), userRowId);
    }

    /** 사용 가능한 크리덴셜을 복호화해 돌려준다. 미등록이면 즉시 거절한다. */
    protected ApiCredential activeCredential(Long userRowId) {
        UserSecuritiesCredential cred = credentialRepository
            .findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(userRowId, broker(), YNType.N, YNType.Y)
            .orElseThrow(() -> new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED));
        return new ApiCredential(cipher.decrypt(cred.getApiKeyEnc()), cipher.decrypt(cred.getApiSecretEnc()));
    }

    private String issueFor(Long userRowId, ApiCredential cred) {
        try {
            BrokerToken token = issueToken(cred.apiKey(), cred.apiSecret());
            if (token == null || token.accessToken() == null) {
                throw new ExternalServiceException(DeskErrorCode.SECURITIES_AUTH_ERROR);
            }
            tokenStore.put(broker(), userRowId, token.accessToken(),
                Math.max(0L, token.expiresInSeconds() - EXPIRY_BUFFER_SECONDS));
            log.debug("{} 사용자 토큰 발급 완료 (userRowId={}, expiresIn={}s)",
                broker(), userRowId, token.expiresInSeconds());
            return token.accessToken();
        } catch (RestClientException e) {
            invalidate(userRowId);
            log.error("{} 사용자 토큰 발급 실패 (userRowId={})", broker(), userRowId, e);
            throw new ExternalServiceException(DeskErrorCode.SECURITIES_AUTH_ERROR, e);
        }
    }
}
