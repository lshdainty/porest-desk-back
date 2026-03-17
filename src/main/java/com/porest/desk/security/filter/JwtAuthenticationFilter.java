package com.porest.desk.security.filter;

import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.controller.TokenExchangeController;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.principal.JwtClaimsPrincipal;
import com.porest.desk.security.principal.JwtUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long RENEWAL_THRESHOLD_MS = 600_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.isTokenValid(token)) {
            JwtClaimsPrincipal claims = jwtTokenProvider.validateAndGetClaims(token);
            JwtUserPrincipal principal = new JwtUserPrincipal(claims);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            long remainingMs = jwtTokenProvider.getRemainingExpiration(token);
            if (remainingMs > 0 && remainingMs < RENEWAL_THRESHOLD_MS) {
                String newToken = jwtTokenProvider.createAccessToken(
                    claims.getUserId(), claims.getUserName(),
                    claims.getUserEmail(), claims.getUserRowId()
                );
                renewAccessTokenCookie(response, newToken);
                log.debug("Token renewed for user: {}", claims.getUserId());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieToken = Arrays.stream(cookies)
                    .filter(c -> TokenExchangeController.ACCESS_TOKEN_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            if (StringUtils.hasText(cookieToken)) {
                return cookieToken;
            }
        }

        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void renewAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from(TokenExchangeController.ACCESS_TOKEN_COOKIE, accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtProperties.getAccessTokenExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
