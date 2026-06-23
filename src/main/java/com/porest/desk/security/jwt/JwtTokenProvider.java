package com.porest.desk.security.jwt;

import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.principal.JwtClaimsPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    /** 임베드 토큰 만료(ms). 60초 — 차트 임베드 등 단명 컨텍스트 전용. */
    public static final long EMBED_TOKEN_EXPIRATION_MS = 60_000L;

    public String createAccessToken(String userId, String userName, String userEmail, Long userRowId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
            .subject(userId)
            .claim("userName", userName)
            .claim("userEmail", userEmail)
            .claim("userRowId", userRowId)
            .claim("typ", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * 임베드용 단명(60초) 토큰. 메인 secret 으로 서명되어 JwtAuthenticationFilter 가
     * 그대로 인식한다. claim {@code typ=embed} 로 필터의 쿠키 갱신을 skip 시킨다.
     * 차트 WebView 처럼 외부 컨텍스트로 한 번 노출되는 단명 컨텍스트에 사용.
     */
    public String createEmbedToken(String userId, String userName, String userEmail, Long userRowId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EMBED_TOKEN_EXPIRATION_MS);

        return Jwts.builder()
            .subject(userId)
            .claim("userName", userName)
            .claim("userEmail", userEmail)
            .claim("userRowId", userRowId)
            .claim("typ", "embed")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
    }

    public JwtClaimsPrincipal validateAndGetClaims(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String typ = claims.get("typ", String.class);
        return new JwtClaimsPrincipal(
            claims.getSubject(),
            claims.get("userName", String.class),
            claims.get("userEmail", String.class),
            claims.get("userRowId", Long.class),
            typ == null ? "access" : typ // 기존 토큰 호환(typ 미존재 → access)
        );
    }

    public Claims validateSsoToken(String ssoToken) {
        SecretKey ssoKey = Keys.hmacShaKeyFor(jwtProperties.getSsoSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(ssoKey)
            .build()
            .parseSignedClaims(ssoToken)
            .getPayload();
    }

    public String createServiceToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 60000); // 1분 유효
        SecretKey ssoKey = Keys.hmacShaKeyFor(jwtProperties.getSsoSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(expiry)
            .claim("type", "access")            // SSO JwtAuthenticationFilter의 isAccessToken() 통과 필수
            .claim("services", Collections.emptyList()) // getServices() NPE 방지
            .signWith(ssoKey)
            .compact();
    }

    public long getRemainingExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT token invalid: {}", e.getMessage());
        }
        return false;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
