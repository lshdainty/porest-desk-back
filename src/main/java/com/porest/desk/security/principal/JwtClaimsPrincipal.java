package com.porest.desk.security.principal;

public record JwtClaimsPrincipal(
    String userId,
    String userName,
    String userEmail,
    Long userRowId
) {}
