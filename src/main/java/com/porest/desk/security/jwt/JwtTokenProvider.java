package com.porest.desk.security.jwt;

import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.principal.JwtClaimsPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    /** SSO RS256 토큰 검증용 JWKS 공개키 Locator(kid 매칭). */
    private final Locator<Key> ssoSigningKeyLocator;

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

    /**
     * SSO 토큰(RS256) 검증 — SSO JWKS 의 공개키로 서명 검증 후 claims 반환.
     * (SSO 가 HMAC 공유 secret → RS256 비대칭 서명으로 전환됨. 클라는 공개키 검증만.)
     */
    public Claims validateSsoToken(String ssoToken) {
        return Jwts.parser()
            .keyLocator(ssoSigningKeyLocator)
            .build()
            .parseSignedClaims(ssoToken)
            .getPayload();
    }

    /**
     * desk→SSO 서비스 호출용 단명 토큰(비밀번호 변경 프록시 등). 아직 공유 secret(HMAC) 서명.
     * <p>⚠️ SSO 가 RS256 전용이 되면 이 HMAC 토큰은 SSO 에서 거부된다 — 별도 마이그레이션 필요
     * (서비스 간 인증을 SSO 가 따로 수용하거나, 사용자 SSO 토큰을 위임하는 방식).
     */
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
