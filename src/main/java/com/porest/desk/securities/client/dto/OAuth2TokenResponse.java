package com.porest.desk.securities.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 client_credentials 토큰 응답. 토스·나무 모두 표준 필드명을 쓴다.
 *
 * <p>응답 모양이 같다고 발급 방식까지 같은 건 아니다 — 토스는 폼 바디, 나무는 쿼리
 * 파라미터로 자격증명을 보낸다. 그 차이는 {@code BrokerTokenManager} 구현이 흡수한다.
 */
public record OAuth2TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
