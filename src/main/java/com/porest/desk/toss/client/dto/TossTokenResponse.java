package com.porest.desk.toss.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 토큰 발급 성공 응답.<br>
 * {@code /oauth2/token} 은 BFF 공통 envelope 이 아닌 OAuth2 표준 포맷으로 응답한다.
 *
 * @param accessToken JWT 형식 access token. 모든 API 의 {@code Authorization: Bearer} 헤더에 사용
 * @param tokenType   토큰 타입. 항상 {@code Bearer}
 * @param expiresIn   만료까지 남은 초 (예: 86400)
 */
public record TossTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
