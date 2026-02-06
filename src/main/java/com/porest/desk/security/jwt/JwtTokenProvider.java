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
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    public String createAccessToken(String userId, String userName, String userEmail, Long userRowId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
            .subject(userId)
            .claim("userName", userName)
            .claim("userEmail", userEmail)
            .claim("userRowId", userRowId)
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

        return new JwtClaimsPrincipal(
            claims.getSubject(),
            claims.get("userName", String.class),
            claims.get("userEmail", String.class),
            claims.get("userRowId", Long.class)
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
