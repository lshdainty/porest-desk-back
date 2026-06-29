package com.porest.desk.security.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.porest.core.exception.UnauthorizedException;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO OAuth2 토큰 엔드포인트 클라이언트.
 *
 * <p>표준 Authorization Code 흐름에서 클라이언트 백엔드(BFF)가 SSO 의 {@code /oauth2/token} 에
 * 인가코드(code) + PKCE code_verifier 를 교환해 SSO access_token(JWT)을 받아온다.
 * 받아온 SSO 토큰은 기존 {@code exchangeToken} 경로로 검증·자체 desk JWT 발급에 재사용된다.
 */
@Slf4j
@Component
public class SsoOAuth2Client {

    private static final String TOKEN_ENDPOINT = "/api/v1/oauth2/token";
    private static final String DESK_CLIENT_ID = "desk";
    private static final String GRANT_TYPE = "authorization_code";

    private final RestTemplate ssoRestTemplate;

    public SsoOAuth2Client(@Qualifier("ssoRestTemplate") RestTemplate ssoRestTemplate) {
        this.ssoRestTemplate = ssoRestTemplate;
    }

    /** SSO /oauth2/token 에 인가코드를 교환해 SSO access_token(JWT)을 반환. 실패 시 AUTH_EXCHANGE_FAILED. */
    public String exchangeCodeForToken(String code, String codeVerifier, String redirectUri) {
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
            return envelope.data().accessToken();
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO code exchange failed: {}", e.getMessage());
            throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
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
