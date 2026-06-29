package com.porest.desk.security.controller.dto;

public class TokenExchangeDto {
    public record Request(String ssoToken) {}
    /** Authorization Code 교환 요청 — 인가코드 + PKCE code_verifier + redirect_uri. */
    public record CodeRequest(String code, String codeVerifier, String redirectUri) {}
    public record Response(String accessToken, String userId, String userName, String userEmail) {}
    public record CheckResponse(Long rowId, String userId, String userName, String userEmail, String timezone) {}

    /** 임베드(차트 WebView 등) 단명 컨텍스트용 토큰. expiresIn 은 초 단위. */
    public record EmbedTokenResponse(String token, long expiresIn) {}
}
