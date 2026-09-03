package com.porest.desk.security.handler;

import tools.jackson.databind.ObjectMapper;
import com.porest.core.controller.ApiResponse;
import com.porest.core.exception.ErrorCodeProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 시큐리티 필터 안에서 <b>컨트롤러와 같은 봉투</b>({@link ApiResponse})로 에러를 쓰는 자리.
 *
 * <p>401 은 {@code @RestControllerAdvice} 로 못 잡는다(QA 2026-09-03 #74). 미인증 요청은
 * {@code AuthorizationFilter} 가 DispatcherServlet 앞에서 끊고 {@code ExceptionTranslationFilter}
 * 가 {@link org.springframework.security.web.AuthenticationEntryPoint} 를 부르므로, 예외가 MVC
 * 예외 처리로 흐르지 않는다. 그래서 봉투를 여기서 직접 만든다 — 종전엔
 * {@code {"status":401,"message":"인증이 필요합니다."}} 라는 <b>다른 모양</b>이 나갔고,
 * 클라이언트가 쓰는 {@code success}·{@code code} 가 401 에만 없었다.
 *
 * <p><b>로케일도 여기서 직접 정한다.</b> {@code LocaleContextHolder} 는 DispatcherServlet 이 채우는데
 * 그건 이 뒤에 선다. MVC 와 같은 답을 내려면 MVC 가 쓰는 {@link LocaleResolver} 를 그대로 불러야 한다
 * (없이 두면 Accept-Language 가 en 이어도 한국어가 나갔다 — 종전 동작).
 * 다만 {@code ?lang=} 파라미터(LocaleChangeInterceptor)는 MVC 인터셉터라 여기까지 오지 않는다.
 *
 * <p>{@link ObjectMapper} 는 MVC 가 쓰는 그 빈이다({@code tools.jackson} — 부트 4 는 Jackson 3 을 쓴다).
 * 여기서 새로 만들면 직렬화 설정이 갈려 같은 봉투가 자리마다 달라질 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorResponder {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCodeProvider errorCode)
            throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        // MessageResolver 와 같은 규칙 — 키가 없으면 코드 문자열로 떨어진다.
        String message = messageSource.getMessage(
                errorCode.getMessageKey(), null, errorCode.getCode(), locale);

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode.getCode(), message));
    }
}
