package com.porest.desk.security.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.controller.dto.TokenExchangeDto;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.security.service.TokenExchangeService;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class TokenExchangeController {
    private final TokenExchangeService tokenExchangeService;
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public static final String ACCESS_TOKEN_COOKIE = "desk_access_token";

    /**
     * OAuth2 Authorization Code 교환 — 인가코드(code)+PKCE code_verifier 를 SSO 에 교환해
     * 자체 desk JWT 를 발급(httpOnly 쿠키). SSO 로그인 완료 후 desk 세션 진입점.
     */
    @PostMapping("/exchange-code")
    public ApiResponse<TokenExchangeDto.Response> exchangeCode(
            @RequestBody TokenExchangeDto.CodeRequest request,
            HttpServletResponse response) {
        TokenExchangeDto.Response exchangeResponse = tokenExchangeService.exchangeCode(
                request.code(), request.codeVerifier(), request.redirectUri());
        setAccessTokenCookie(response, exchangeResponse.accessToken());
        return ApiResponse.success(exchangeResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        return ApiResponse.success(null);
    }

    @GetMapping("/check")
    public ApiResponse<TokenExchangeDto.CheckResponse> checkLogin(@LoginUser UserPrincipal loginUser) {
        // 가입일시(User.createAt) 노출 — PK 단건 조회. 유저 미존재/미조회 시 null.
        LocalDateTime joinedAt = userRepository.findById(loginUser.getRowId())
            .map(User::getCreateAt)
            .orElse(null);
        return ApiResponse.success(new TokenExchangeDto.CheckResponse(
            loginUser.getRowId(),
            loginUser.getUserId(),
            loginUser.getUserName(),
            loginUser.getUserEmail(),
            "Asia/Seoul",
            joinedAt
        ));
    }

    /**
     * 임베드(차트 WebView 등) 단명 컨텍스트용 60초 토큰 발급.
     * 인증된 사용자만 호출 가능. 발급된 토큰은 Authorization: Bearer 로 사용하며,
     * JwtAuthenticationFilter 는 typ=embed 토큰의 쿠키 갱신을 skip 한다.
     */
    @PostMapping("/embed-token")
    public ApiResponse<TokenExchangeDto.EmbedTokenResponse> issueEmbedToken(@LoginUser UserPrincipal loginUser) {
        String token = jwtTokenProvider.createEmbedToken(
            loginUser.getUserId(),
            loginUser.getUserName(),
            loginUser.getUserEmail(),
            loginUser.getRowId()
        );
        return ApiResponse.success(new TokenExchangeDto.EmbedTokenResponse(
            token, JwtTokenProvider.EMBED_TOKEN_EXPIRATION_MS / 1000
        ));
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtProperties.getAccessTokenExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
