package com.porest.desk.security.principal;

public record JwtClaimsPrincipal(
    String userId,
    String userName,
    String userEmail,
    Long userRowId,
    /** 토큰 유형. "access"(기본 로그인) 또는 "embed"(60초 임베드 차트 등). 갱신 필터는 embed 갱신 skip. */
    String tokenType
) {
    public boolean isEmbed() { return "embed".equals(tokenType); }
}
