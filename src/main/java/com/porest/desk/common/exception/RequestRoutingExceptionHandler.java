package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.message.MessageKey;
import com.porest.core.util.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요청이 <b>어느 핸들러에도 닿지 못했을 때</b> 500 대신 404·405 를 돌려주는 핸들러.
 *
 * <p>QA 2026-09-03 #64 — 로그인 상태로 {@code POST /api/v1/expense/budgets} ·
 * {@code POST /api/v1/saving-goals} 를 부르면 {@code COMMON_500} 이 나왔다. 둘 다 <b>경로는 있고
 * 메서드만 없는</b> 자리다(각각 {@code @GetMapping} 뿐). Spring 은 이때
 * {@link HttpRequestMethodNotSupportedException} 을 던지는데 porest-core 의 GlobalExceptionHandler
 * 가 이걸 매핑하지 않아 마지막 {@code @ExceptionHandler(Exception.class)} 로 떨어졌다 —
 * 클라이언트 실수가 서버 장애로 둔갑해 모니터링 5xx 에 섞인다.
 *
 * <p><b>매핑이 아예 없는 경로는 이미 404 다.</b> 정적 리소스 핸들러(`/**`)가 먼저 먹고
 * {@link org.springframework.web.servlet.resource.NoResourceFoundException} 을 던지며, core 가 그걸
 * {@code COMMON_404} 로 잡는다(실측: {@code POST /api/v1/no-such-path} → 404). 그래서 여기서
 * {@link NoHandlerFoundException} 을 잡는 건 <b>지금 도는 경로가 아니라 안전망</b>이다 —
 * {@code spring.web.resources.add-mappings=false} 로 정적 매핑을 끄는 순간 그 예외가 살아나고,
 * 그때 이 핸들러가 없으면 없는 경로가 다시 500 으로 나간다. 응답은 core 의 NoResourceFound
 * 핸들러와 <b>글자까지 같게</b> 맞춰 뒀다(같은 코드 · 같은 메시지 키).
 *
 * <p>{@code spring.mvc.throw-exception-if-no-handler-found} 는 <b>건드리지 않는다</b>. Boot 3.2 부터
 * deprecated(level=error, "should no longer need to be configured")이고, 켜 봐야 정적 리소스 핸들러가
 * 먼저 매칭돼 아무 효과가 없다. 정적 매핑을 끄는 쪽이 유일하게 의미 있는 조작인데 그건
 * swagger-ui 같은 classpath 리소스를 같이 죽인다.
 *
 * <p>{@code @Order} 가 핵심이다 — {@link RequestValueExceptionHandler} 와 같은 이유다. Spring 은
 * advice 를 "더 구체적인 예외" 로 고르지 않고 순서대로 훑다가 처음 매칭에서 멈추므로, core 의
 * {@code Exception} 핸들러보다 앞서지 않으면 이 클래스는 한 번도 안 불리면서 컴파일도 테스트도
 * 통과한다.
 *
 * <p>인증 여부로 결과가 갈렸던 것도 여기서 정리된다. 미인증 요청은 시큐리티
 * {@code AuthorizationFilter} 가 DispatcherServlet 앞에서 끊어 401 을 내므로 핸들러 조회 자체가
 * 일어나지 않는다(실측). 즉 404·405 는 <b>인증을 통과한 요청에서만</b> 보인다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class RequestRoutingExceptionHandler {

    private final MessageResolver messageResolver;

    /** 경로는 있는데 메서드가 없다 — 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 메서드: method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());

        DeskErrorCode code = DeskErrorCode.METHOD_NOT_ALLOWED;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getHttpStatus());
        // RFC 9110 은 405 에 Allow 를 요구한다. 없으면 클라이언트가 무엇을 써야 하는지 알 길이 없다.
        allowHeader(e.getSupportedHttpMethods()).ifPresent(v -> builder.header(HttpHeaders.ALLOW, v));

        return builder.body(ApiResponse.error(code.getCode(), messageResolver.getMessage(code)));
    }

    /**
     * 매핑이 아예 없다 — 404. 정적 리소스 매핑을 끈 구성에서만 실제로 던져진다(클래스 주석 참고).
     * 코드·메시지는 core 의 {@code NoResourceFoundException} 응답과 같은 것을 쓴다.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("매핑 없는 경로: method={}, path={}", e.getHttpMethod(), e.getRequestURL());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("COMMON_404", messageResolver.getMessage(MessageKey.COMMON_404)));
    }

    /** {@code Allow} 헤더 값. 지원 메서드를 모르면(드물다) 헤더를 붙이지 않는다. */
    static java.util.Optional<String> allowHeader(Set<HttpMethod> supported) {
        if (supported == null || supported.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(supported.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", ")));
    }
}
