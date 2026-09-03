package com.porest.desk.security.handler;

import com.porest.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 됐는데 권한이 없을 때의 403 — 401 과 <b>같은 봉투</b>로 답한다.
 *
 * <p>없으면 시큐리티 기본 핸들러가 {@code response.sendError(403)} 를 불러 서블릿 ERROR 디스패치로
 * 넘어가고, 스프링 부트 기본 에러 본문({@code {"timestamp":…,"status":403,"path":…}})이 나간다 —
 * 401 을 고쳐 놓고 403 에 같은 구멍을 남기는 꼴이다.
 *
 * <p>도메인 403({@code SUBS_001} 등)은 여기까지 오지 않는다. 그건 컨트롤러 안에서 던져져 core 의
 * advice 가 이미 같은 봉투로 답한다. 여기는 <b>필터 단계</b>에서 끊긴 403 만 온다. 코드는 core 의
 * {@link ErrorCode#FORBIDDEN}({@code COMMON_412}) — core 의
 * {@code handleAccessDeniedException} 이 내는 것과 같은 코드·같은 메시지 키라, 같은 상황이 어디서
 * 끊기든 응답이 글자까지 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponder responder;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("권한 없는 접근 시도 - URI: {}, Method: {}", request.getRequestURI(), request.getMethod());
        responder.write(request, response, ErrorCode.FORBIDDEN);
    }
}
