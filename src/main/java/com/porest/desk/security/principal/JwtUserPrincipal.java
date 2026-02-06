package com.porest.desk.security.principal;

import com.porest.core.security.AuditorPrincipal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtUserPrincipal implements AuditorPrincipal {
    private final JwtClaimsPrincipal claims;

    @Override
    public String getUserId() { return claims.userId(); }
    public String getUserName() { return claims.userName(); }
    public String getUserEmail() { return claims.userEmail(); }
    public Long getUserRowId() { return claims.userRowId(); }
}
