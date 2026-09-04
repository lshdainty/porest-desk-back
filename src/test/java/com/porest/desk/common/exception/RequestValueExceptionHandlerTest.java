package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.controller.GlobalExceptionHandler;
import com.porest.core.util.MessageResolver;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 요청값 오류가 500 이 아니라 400 으로 변환되는지 검증(QA 2026-09-03 #8 #25 #33).
 *
 * <p>매핑 자체는 각 컨트롤러 슬라이스의 MockMvc 400 테스트가 함께 잡는다. 다만 그 테스트들은
 * <b>advice 순서를 검증하지 못한다</b> — {@code @Order} 를 떼도 {@code scanBasePackages} 등록 순서
 * 덕에 desk advice 가 우연히 먼저 등록돼 그대로 통과한다(실측). 순서는 아래 전용 테스트가 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class RequestValueExceptionHandlerTest {

    @Mock private MessageResolver messageResolver;

    @InjectMocks private RequestValueExceptionHandler sut;

    @Test
    @DisplayName("DateTimeParseException → 400 INVALID_DATE_VALUE")
    void mapsDateParseFailureToBadRequest() {
        given(messageResolver.getMessage(DeskErrorCode.INVALID_DATE_VALUE))
                .willReturn("올바른 날짜가 아니에요");

        ResponseEntity<ApiResponse<Void>> res = sut.handleDateTimeParse(
                new DateTimeParseException("Invalid date 'FEBRUARY 30'", "2026-02-30", 0));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_400");
        assertThat(res.getBody().getMessage()).isEqualTo("올바른 날짜가 아니에요");
    }

    @Test
    @DisplayName("원인 사슬에 DateTimeParseException 이 있는 본문 오류 → 날짜 메시지")
    void unreadableBodyCausedByDateUsesDateMessage() {
        given(messageResolver.getMessage(DeskErrorCode.INVALID_DATE_VALUE))
                .willReturn("올바른 날짜가 아니에요");

        // Jackson 이 실제로 만드는 모양: HttpMessageNotReadable → (중간 예외) → DateTimeParseException
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalStateException(
                        new DateTimeParseException("Invalid date 'FEBRUARY 30'", "2026-02-30", 0)),
                null);

        ResponseEntity<ApiResponse<Void>> res = sut.handleUnreadableBody(e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("올바른 날짜가 아니에요");
    }

    @Test
    @DisplayName("날짜와 무관한 본문 오류(깨진 JSON·잘못된 enum) → 본문 형식 메시지")
    void unreadableBodyWithoutDateUsesMalformedMessage() {
        given(messageResolver.getMessage(DeskErrorCode.MALFORMED_REQUEST_BODY))
                .willReturn("요청 형식이 올바르지 않아요");

        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalStateException("Unexpected character '}'"),
                null);

        ResponseEntity<ApiResponse<Void>> res = sut.handleUnreadableBody(e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("요청 형식이 올바르지 않아요");
    }

    @Test
    @DisplayName("필수 파라미터 누락 → 400 + 이름 없는 문구 (QA 2026-09-04 #75)")
    void missingParameterAnswersWithoutTheName() {
        given(messageResolver.getMessage(DeskErrorCode.MISSING_PARAMETER))
                .willReturn("요청에 빠진 값이 있어요");

        ResponseEntity<ApiResponse<Void>> res = sut.handleMissingParameter(
                new MissingServletRequestParameterException("date", "LocalDate"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        // 코드는 core 가 답하던 것과 같다 — 바뀐 건 문구뿐이다.
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_400");
        assertThat(res.getBody().getMessage()).isEqualTo("요청에 빠진 값이 있어요");
        // 인자를 넘기면 Spring 이 값을 MessageFormat 에 태운다 — 따옴표 하나에 문구가 사라진다.
        verify(messageResolver, never()).getMessage(any(DeskErrorCode.class), any(Object[].class));
    }

    @Test
    @DisplayName("파라미터 이름은 로그에만 남는다 — 이 로그를 지우면 디버깅 정보가 사라진다")
    void parameterNamesSurviveInTheLog() {
        given(messageResolver.getMessage(DeskErrorCode.MISSING_PARAMETER)).willReturn("요청에 빠진 값이 있어요");
        given(messageResolver.getMessage(DeskErrorCode.INVALID_PARAMETER_VALUE)).willReturn("요청 값이 올바르지 않아요");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            sut.handleMissingParameter(new MissingServletRequestParameterException("endDate", "LocalDate"));
            // 실물 생성자는 MethodParameter 를 요구한다 — 이름만 필요하므로 mock 으로 세운다
            // (실물 예외로 도는 경로는 ErrorEnvelopeTest 가 HTTP 로 건다).
            MethodArgumentTypeMismatchException mismatch = mock(MethodArgumentTypeMismatchException.class);
            given(mismatch.getName()).willReturn("id");
            sut.handleTypeMismatch(mismatch);

            assertThat(appender.list)
                    .extracting(e -> e.getFormattedMessage())
                    .anySatisfy(m -> assertThat(m).contains("endDate"))
                    .anySatisfy(m -> assertThat(m).contains("id"));
        } finally {
            detachAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestValueExceptionHandler.class);
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(RequestValueExceptionHandler.class)).detachAppender(appender);
    }

    @Test
    @DisplayName("advice 순서 — core GlobalExceptionHandler 보다 먼저 훑힌다")
    void outranksCoreFallbackAdvice() {
        // Spring 은 advice 를 "더 구체적인 예외 핸들러" 로 고르지 않고 순서대로 훑다가 처음 매칭에서 멈춘다.
        // core 쪽엔 @ExceptionHandler(Exception.class) 가 있어, 이 advice 가 뒤에 서면 한 번도 안 불린다.
        // 종전엔 등록 순서 덕에 우연히 이겼을 뿐이라 MockMvc 테스트로는 이 회귀가 안 잡힌다.
        int desk = OrderUtils.getOrder(RequestValueExceptionHandler.class, Ordered.LOWEST_PRECEDENCE);
        int core = OrderUtils.getOrder(GlobalExceptionHandler.class, Ordered.LOWEST_PRECEDENCE);

        assertThat(desk).isLessThan(core);
    }
}
