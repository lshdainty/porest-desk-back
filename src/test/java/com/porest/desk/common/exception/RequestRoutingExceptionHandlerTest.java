package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.controller.GlobalExceptionHandler;
import com.porest.core.message.MessageKey;
import com.porest.core.util.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 라우팅 실패(경로 없음 · 메서드 불일치)가 500 이 아니라 404 · 405 가 되는지 검증(QA 2026-09-03 #64).
 *
 * <p>405 는 {@code ApiRoutingErrorTest} 가 진짜 톰캣으로도 한 번 더 못 박는다. 반면
 * {@link NoHandlerFoundException} 은 <b>지금 구성에서는 던져지지 않으므로</b>(정적 리소스 핸들러가
 * 먼저 먹는다) 그쪽 테스트로는 잡히지 않는다 — 이 클래스가 핸들러를 직접 불러 그 안전망을 지킨다.
 */
@ExtendWith(MockitoExtension.class)
class RequestRoutingExceptionHandlerTest {

    @Mock private MessageResolver messageResolver;

    @InjectMocks private RequestRoutingExceptionHandler sut;

    @Test
    @DisplayName("메서드 불일치 → 405 COMMON_405 + Allow 헤더")
    void mapsMethodMismatchToMethodNotAllowed() {
        given(messageResolver.getMessage(DeskErrorCode.METHOD_NOT_ALLOWED))
                .willReturn("이 주소에서는 쓸 수 없는 요청 방식이에요");

        ResponseEntity<ApiResponse<Void>> res = sut.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PATCH")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET, PATCH");
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_405");
        assertThat(res.getBody().getMessage()).isEqualTo("이 주소에서는 쓸 수 없는 요청 방식이에요");
    }

    @Test
    @DisplayName("지원 메서드를 모르면 Allow 헤더를 붙이지 않는다")
    void omitsAllowHeaderWhenSupportedMethodsUnknown() {
        given(messageResolver.getMessage(DeskErrorCode.METHOD_NOT_ALLOWED)).willReturn("m");

        ResponseEntity<ApiResponse<Void>> res = sut.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getHeaders().getFirst(HttpHeaders.ALLOW)).isNull();
    }

    @Test
    @DisplayName("Allow 값은 정렬된 메서드 목록")
    void allowHeaderIsSortedMethodList() {
        assertThat(RequestRoutingExceptionHandler.allowHeader(Set.of(HttpMethod.PUT, HttpMethod.GET)))
                .contains("GET, PUT");
        assertThat(RequestRoutingExceptionHandler.allowHeader(Set.of())).isEmpty();
        assertThat(RequestRoutingExceptionHandler.allowHeader(null)).isEmpty();
    }

    @Test
    @DisplayName("매핑 없음 → 404, core 의 NoResourceFound 응답과 같은 코드·메시지")
    void mapsNoHandlerFoundToNotFound() {
        given(messageResolver.getMessage(MessageKey.COMMON_404)).willReturn("존재하지 않는 리소스입니다.");

        ResponseEntity<ApiResponse<Void>> res = sut.handleNoHandlerFound(
                new NoHandlerFoundException("POST", "/api/v1/no-such-path", new HttpHeaders()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_404");
        assertThat(res.getBody().getMessage()).isEqualTo("존재하지 않는 리소스입니다.");
    }

    @Test
    @DisplayName("advice 순서 — core GlobalExceptionHandler 보다 먼저 훑힌다")
    void outranksCoreFallbackAdvice() {
        // 이게 뒤집히면 core 의 @ExceptionHandler(Exception.class) 가 먼저 잡아 다시 500 이 된다.
        // 컴파일도 다른 테스트도 통과하므로 여기서 따로 못 박는다.
        int desk = OrderUtils.getOrder(RequestRoutingExceptionHandler.class, Ordered.LOWEST_PRECEDENCE);
        int core = OrderUtils.getOrder(GlobalExceptionHandler.class, Ordered.LOWEST_PRECEDENCE);

        assertThat(desk).isLessThan(core);
    }
}
