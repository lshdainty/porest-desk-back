package com.porest.desk.security.filter;

import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.controller.TokenExchangeController;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.principal.JwtClaimsPrincipal;
import com.porest.desk.security.principal.JwtUserPrincipal;
import com.porest.desk.security.service.TokenExchangeService;
import com.porest.desk.security.session.store.SessionRevocationStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    /**
     * 지연 조회 — 빈을 직접 주입하면 애플리케이션이 뜨지 않는다.
     *
     * <p>톰캣이 필터 빈을 모으는 시점({@code ServletContextInitializerBeans})은 JPA 초기화보다
     * 앞선다. 여기서 TokenExchangeService 를 직접 주입하면 그 시점에
     * {@code UserRepository → QueryDslConfig → EntityManager} 가 통째로 끌려 들어와
     * "No qualifying bean of type EntityManager" 로 기동이 깨진다.
     *
     * <p>ObjectProvider 는 주입 시점에 빈을 만들지 않는다. 실제로 재인증이 필요한 요청이
     * 왔을 때 꺼내 쓰므로, 그때는 컨텍스트가 이미 다 올라와 있다.
     */
    private final ObjectProvider<TokenExchangeService> tokenExchangeServiceProvider;
    /**
     * 로그아웃한 세션의 표식 저장소.
     *
     * <p>위 ObjectProvider 와 달리 직접 주입해도 된다 — 이 빈이 끌고 오는 것은
     * {@code StringRedisTemplate} 뿐이고(메모리 구현은 의존이 아예 없다) JPA 를 건드리지 않는다.
     */
    private final SessionRevocationStore sessionRevocationStore;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long RENEWAL_THRESHOLD_MS = 600_000L;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            if (jwtTokenProvider.isTokenValid(token)) {
                JwtClaimsPrincipal claims = jwtTokenProvider.validateAndGetClaims(token);
                // 로그아웃한 세션의 토큰은 서명과 exp 가 멀쩡해도 통과시키지 않는다. 인증을
                // 세우지 않고 그대로 흘려보내면 뒤에서 401 이 된다 — 여기서 예외를 던지면
                // 인증이 필요 없는 엔드포인트까지 같이 막힌다.
                //
                // 갱신도 같이 건너뛴다(아래 블록에 못 들어간다). 이게 이 검사의 절반이다 —
                // 갱신 경로는 세션을 보지 않고 claims 만으로 재서명하므로, 여기서 안 막으면
                // 복사된 쿠키를 만료 전에 한 번 쓰는 것만으로 수명이 무기한 연장된다.
                if (!isRevokedSession(claims)) {
                    authenticate(claims);

                    // 임베드 토큰(typ=embed)은 단명 컨텍스트 전용이라 쿠키 갱신 대상에서 제외한다.
                    // 60초 < RENEWAL_THRESHOLD 라 항상 갱신 트리거되는 부작용을 막고,
                    // 임베드용 토큰이 access cookie 로 승격되지 않도록 보장한다.
                    if (!claims.isEmbed()) {
                        long remainingMs = jwtTokenProvider.getRemainingExpiration(token);
                        if (remainingMs > 0 && remainingMs < RENEWAL_THRESHOLD_MS) {
                            String newToken = jwtTokenProvider.createAccessToken(
                                claims.userId(), claims.userName(),
                                claims.userEmail(), claims.userRowId(), claims.sessionId()
                            );
                            renewAccessTokenCookie(response, newToken);
                            log.debug("Token renewed for user: {}", claims.userId());
                        }
                    }
                }
            } else {
                trySilentReauth(token, response);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 만료된 토큰을 조용히 새 것으로 바꾼다 — 사용자는 로그인 화면을 보지 않는다.
     *
     * <p>인증이 풀리는 자리는 여기 하나다. 개별 API 마다 다시 처리하지 않는다.
     *
     * <p>안 되면 아무것도 하지 않고 넘긴다. 인증 없는 요청이 되어 401 로 떨어지고, 클라이언트가
     * 로그인으로 보낸다 — 여기서 예외를 던져 응답을 가로채면 원래 에러가 가려진다.
     */
    private void trySilentReauth(String token, HttpServletResponse response) {
        JwtClaimsPrincipal expired = jwtTokenProvider.parseExpiredClaims(token);
        // 폐기 표식을 여기서 또 보지 않는다. 이 경로는 결국 SsoSessionService.refresh 를 타는데,
        // 그쪽은 살아 있는 세션 행(is_deleted='N')을 찾지 못하면 재발급을 거절한다 — DB 가
        // 표식보다 강한 근거이고, 표식이 TTL 로 사라진 뒤에도 계속 막힌다.
        //
        // 만료가 아닌 이유로 못 읽었거나(위조·형식 오류), 세션에 속하지 않는 토큰(임베드,
        // 세션 도입 前 발급분)은 재발급 대상이 아니다.
        if (expired == null || expired.isEmbed() || !expired.hasSession()) {
            return;
        }

        String newToken;
        try {
            newToken = tokenExchangeServiceProvider.getObject().reauthenticate(expired);
        } catch (Exception e) {
            // 재발급 실패가 요청 자체를 500 으로 만들면 안 된다 — 로그인하면 풀릴 일이다.
            log.error("무음 재인증 실패. userId={}, err={}", expired.userId(), e.getMessage());
            return;
        }
        if (newToken == null) {
            return;
        }

        authenticate(jwtTokenProvider.validateAndGetClaims(newToken));
        renewAccessTokenCookie(response, newToken);
        log.debug("무음 재인증 완료. userId={}", expired.userId());
    }

    /**
     * 세션에 속하지 않는 토큰(임베드, 세션 도입 前 발급분)은 조회 자체를 건너뛴다 — 폐기할
     * 세션이 없으니 표식도 있을 수 없고, 요청마다 헛조회를 하게 된다.
     */
    private boolean isRevokedSession(JwtClaimsPrincipal claims) {
        return claims.hasSession() && sessionRevocationStore.isRevoked(claims.sessionId());
    }

    private void authenticate(JwtClaimsPrincipal claims) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            new JwtUserPrincipal(claims), null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
                // 토큰 수명이 아니라 세션 수명으로 둔다. 토큰과 같이 1시간 만에 사라지면
                // 만료된 토큰이 서버에 도달조차 못 해 재발급할 세션을 찾을 수 없다.
                .maxAge(jwtProperties.getSessionExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
