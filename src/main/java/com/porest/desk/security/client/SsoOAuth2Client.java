package com.porest.desk.security.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.porest.core.exception.UnauthorizedException;
import com.porest.desk.common.config.properties.AppProperties;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO OAuth2 토큰 엔드포인트 클라이언트.
 *
 * <p>표준 Authorization Code 흐름에서 클라이언트 백엔드(BFF)가 SSO 의 {@code /oauth2/token} 에
 * 인가코드(code) + PKCE code_verifier 를 교환해 SSO access_token(JWT)을 받아온다.
 * 받아온 SSO 토큰은 기존 {@code exchangeToken} 경로로 검증·자체 desk JWT 발급에 재사용된다.
 *
 * <p>또한 표준 client_credentials 그랜트로 desk→SSO 서비스 토큰(RS256)을 발급한다 —
 * 비밀번호 변경/검증 프록시에서 SSO 를 호출할 때 Bearer 로 사용한다(레거시 HMAC 서비스 토큰 대체).
 */
@Slf4j
@Component
public class SsoOAuth2Client {

    private static final String TOKEN_ENDPOINT = "/api/v1/oauth2/token";
    private static final String DESK_CLIENT_ID = "desk";
    private static final String GRANT_TYPE = "authorization_code";
    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final RestTemplate ssoRestTemplate;
    private final AppProperties appProperties;

    public SsoOAuth2Client(@Qualifier("ssoRestTemplate") RestTemplate ssoRestTemplate,
                           AppProperties appProperties) {
        this.ssoRestTemplate = ssoRestTemplate;
        this.appProperties = appProperties;
    }

    /**
     * SSO /oauth2/token 에 인가코드를 교환해 access + refresh 를 반환. 실패 시 AUTH_EXCHANGE_FAILED.
     *
     * <p>refresh 는 세션에 보관했다가 access 가 만료되면 조용히 재발급하는 데 쓴다. 예전에는
     * 받아 놓고 버렸고, 그래서 SSO refresh 수명(7일)이 있는데도 1시간마다 로그인 화면을 봤다.
     */
    public TokenPair exchangeCodeForToken(String code, String codeVerifier, String redirectUri) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", GRANT_TYPE);
        body.put("code", code);
        body.put("code_verifier", codeVerifier);
        body.put("client_id", DESK_CLIENT_ID);
        body.put("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            TokenEnvelope envelope = ssoRestTemplate.postForObject(
                    TOKEN_ENDPOINT, new HttpEntity<>(body, headers), TokenEnvelope.class);
            if (envelope == null || envelope.data() == null
                    || envelope.data().accessToken() == null || envelope.data().accessToken().isBlank()) {
                log.error("SSO token exchange returned empty access_token");
                throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
            }
            return new TokenPair(envelope.data().accessToken(), envelope.data().refreshToken());
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO code exchange failed: {}", e.getMessage());
            throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
        }
    }

    /**
     * SSO /oauth2/token 에 refresh_token 그랜트로 재발급 — 사용자 개입 없이 서버끼리 돈다.
     *
     * <p>실패를 두 갈래로 나눠 돌려준다. 이걸 뭉뚱그리면 SSO 가 잠깐 죽었을 때 전체 사용자가
     * 로그아웃된다.
     * <ul>
     *   <li>4xx (거부) → {@code null}. refresh 가 만료·폐기됐다는 확정 답이라 세션을 끊어야 한다.</li>
     *   <li>그 밖(5xx·타임아웃) → 예외. 일시적 장애이므로 세션은 그대로 두고 다음 요청에 다시 시도한다.</li>
     * </ul>
     */
    public TokenPair refreshTokens(String refreshToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", GRANT_TYPE_REFRESH_TOKEN);
        body.put("refresh_token", refreshToken);
        body.put("client_id", DESK_CLIENT_ID);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        TokenEnvelope envelope;
        try {
            envelope = ssoRestTemplate.postForObject(
                    TOKEN_ENDPOINT, new HttpEntity<>(body, headers), TokenEnvelope.class);
        } catch (HttpClientErrorException e) {
            // SSO 가 명시적으로 거부했다 — 만료·폐기·권한 회수. 다시 시도해도 결과는 같다.
            log.info("SSO refresh rejected: {}", e.getStatusCode());
            return null;
        }

        if (envelope == null || envelope.data() == null
                || envelope.data().accessToken() == null || envelope.data().accessToken().isBlank()) {
            log.error("SSO refresh returned empty access_token");
            return null;
        }
        return new TokenPair(envelope.data().accessToken(), envelope.data().refreshToken());
    }

    /**
     * SSO /oauth2/token 에 client_credentials 그랜트로 desk 서비스 토큰(RS256)을 발급받아 access_token 을 반환.
     * <p>비밀번호 변경/검증 프록시에서 SSO 를 호출할 때 Bearer 로 사용한다. 실패 시 AUTH_EXCHANGE_FAILED.
     */
    public String issueServiceToken() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", GRANT_TYPE_CLIENT_CREDENTIALS);
        body.put("client_id", DESK_CLIENT_ID);
        body.put("client_secret", appProperties.getSso().getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            TokenEnvelope envelope = ssoRestTemplate.postForObject(
                    TOKEN_ENDPOINT, new HttpEntity<>(body, headers), TokenEnvelope.class);
            if (envelope == null || envelope.data() == null
                    || envelope.data().accessToken() == null || envelope.data().accessToken().isBlank()) {
                log.error("SSO client_credentials returned empty access_token");
                throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
            }
            return envelope.data().accessToken();
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO client_credentials failed: {}", e.getMessage());
            throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
        }
    }

    /** SSO 가 내려준 토큰 한 쌍. refresh 는 client_credentials 응답처럼 없을 수도 있다. */
    public record TokenPair(String accessToken, String refreshToken) {
        public boolean hasRefreshToken() {
            return refreshToken != null && !refreshToken.isBlank();
        }
    }

    /** SSO ApiResponse 래퍼 (success/code/message/data). */
    record TokenEnvelope(boolean success, String code, String message, TokenData data) {}

    /** SSO 표준 토큰 응답 (snake_case). */
    record TokenData(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken) {}
}
