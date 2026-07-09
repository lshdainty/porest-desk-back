package com.porest.desk.security.controller.dto;

import java.time.LocalDateTime;

public class TokenExchangeDto {
    /** Authorization Code 교환 요청 — 인가코드 + PKCE code_verifier + redirect_uri. */
    public record CodeRequest(String code, String codeVerifier, String redirectUri) {}
    public record Response(String accessToken, String userId, String userName, String userEmail) {}
    /** 세션 확인 응답. joinedAt = 가입일시(User.createAt @CreatedDate), 없으면 null. */
    public record CheckResponse(Long rowId, String userId, String userName, String userEmail, String timezone, LocalDateTime joinedAt) {}

    /** 임베드(차트 WebView 등) 단명 컨텍스트용 토큰. expiresIn 은 초 단위. */
    public record EmbedTokenResponse(String token, long expiresIn) {}
}
