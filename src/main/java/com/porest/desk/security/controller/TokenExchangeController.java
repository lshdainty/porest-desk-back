package com.porest.desk.security.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.controller.dto.TokenExchangeDto;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.security.service.TokenExchangeService;
import jakarta.servlet.http.HttpServletResponse;
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

    public static final String ACCESS_TOKEN_COOKIE = "desk_access_token";

    @PostMapping("/exchange")
    public ApiResponse<TokenExchangeDto.Response> exchangeToken(
            @RequestBody TokenExchangeDto.Request request,
            HttpServletResponse response) {
        TokenExchangeDto.Response exchangeResponse = tokenExchangeService.exchangeToken(request.ssoToken());
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
        return ApiResponse.success(new TokenExchangeDto.CheckResponse(
            loginUser.getRowId(),
            loginUser.getUserId(),
            loginUser.getUserName(),
            loginUser.getUserEmail(),
            "Asia/Seoul"
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
