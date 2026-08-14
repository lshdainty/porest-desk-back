package com.porest.desk.security.principal;

public record JwtClaimsPrincipal(
    String userId,
    String userName,
    String userEmail,
    Long userRowId,
    /** 토큰 유형. "access"(기본 로그인) 또는 "embed"(60초 임베드 차트 등). 갱신 필터는 embed 갱신 skip. */
    String tokenType,
    /**
     * 세션 아이디(jti) — 이 토큰이 어느 기기의 세션인지. 만료 후 조용히 재발급할 때 이 값으로
     * 세션을 찾는다. 세션 도입 前에 발급된 토큰에는 없어서 {@code null} 일 수 있고, 임베드
     * 토큰에도 없다(세션에 속하지 않는 단명 토큰이라).
     */
    String sessionId
) {
    public boolean isEmbed() { return "embed".equals(tokenType); }

    public boolean hasSession() { return sessionId != null && !sessionId.isBlank(); }
}
