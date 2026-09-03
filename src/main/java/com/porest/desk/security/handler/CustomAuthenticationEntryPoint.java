package com.porest.desk.security.handler;

import com.porest.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증이 없거나 유효하지 않을 때의 401. 봉투와 말투는 {@link ApiErrorResponder} 가 맞춘다.
 *
 * <p>코드는 core 의 {@link ErrorCode#UNAUTHORIZED}({@code COMMON_411}) 를 그대로 쓴다 — 새 코드를
 * 만들지 않는다. 클라이언트(웹 {@code shared/api/base.ts} · 앱 {@code AuthInterceptor})는 둘 다
 * <b>HTTP 상태로만</b> 401 을 판정하므로 본문에 코드가 생겨도 흐름이 바뀌지 않고, 앱의
 * {@code ApiException.fromDio} 는 {@code code}·{@code message} 를 읽으므로 오히려 정확해진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponder responder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 접근 시도 - URI: {}, Method: {}", request.getRequestURI(), request.getMethod());
        responder.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
