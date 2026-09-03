package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

/**
 * 요청값 자체가 틀렸을 때 500 대신 400 을 돌려주는 핸들러. 뒤에 <b>말투</b>도 여기서 맞춘다
 * (QA 2026-09-03 #74 — 타입 불일치 문구가 core 에 격식체로 박혀 있었다).
 *
 * <p>porest-core 의 GlobalExceptionHandler 는 {@link DateTimeParseException} 도
 * {@link HttpMessageNotReadableException} 도 매핑하지 않아 마지막 {@code @ExceptionHandler(Exception.class)}
 * 로 떨어졌고, 그래서 {@code 2026-02-30} 같은 없는 날짜나 깨진 JSON 이 {@code COMMON_500} 으로 나갔다
 * (QA 2026-09-03 #8 #25 #33). core 는 GitHub Packages 로 배포되는 별도 레포라
 * {@link ConcurrencyExceptionHandler} 와 같은 방식으로 여기서 덮는다.
 *
 * <p><b>{@code @Order} 가 핵심이다.</b> Spring 은 advice 들 사이에서 "더 구체적인 예외 핸들러" 를
 * 고르지 않고 <b>순서대로 훑다가 처음 매칭되는 advice 에서 멈춘다</b>. 이걸 빼면 core 의
 * {@code Exception} 핸들러가 먼저 잡아 이 클래스가 한 번도 안 불리는데, 컴파일도 테스트도
 * 통과해 조용히 무효가 된다 — 슬라이스의 MockMvc 400 테스트조차 못 잡는다({@code scanBasePackages}
 * 등록 순서 덕에 desk advice 가 우연히 먼저 서기 때문. 실측 확인). 그래서 순서는
 * {@code RequestValueExceptionHandlerTest#outranksCoreFallbackAdvice} 가 따로 고정한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class RequestValueExceptionHandler {

    private final MessageResolver messageResolver;

    /** 컨트롤러가 직접 파싱하는 자리(ExpenseApiController · SmsImportApiController 의 WallClockDateTimeParser). */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateTimeParse(DateTimeParseException e) {
        log.warn("날짜 파싱 실패: parsed={}", e.getParsedString());
        return badRequest(DeskErrorCode.INVALID_DATE_VALUE);
    }

    /**
     * Jackson 본문 역직렬화 실패 — 날짜면 날짜 메시지, 아니면 본문 형식 메시지.
     *
     * <p>날짜뿐 아니라 잘못된 enum 값·깨진 JSON·빈 본문도 여기로 온다. 전부 클라이언트 오류이므로
     * 4xx 가 맞다(종전엔 전부 500 이었다).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        DeskErrorCode code = isDateFailure(e)
                ? DeskErrorCode.INVALID_DATE_VALUE
                : DeskErrorCode.MALFORMED_REQUEST_BODY;
        // e.getMessage() 는 DTO 클래스명·패키지 경로를 그대로 담으므로 응답에 싣지 않고 로그로만 남긴다.
        log.warn("요청 본문을 읽지 못함: code={}, cause={}", code.getCode(),
                e.getMostSpecificCause().getMessage());
        return badRequest(code);
    }

    /** 원인 사슬에 DateTimeParseException 이 있으면 날짜 문제로 본다. */
    private static boolean isDateFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof DateTimeParseException) return true;
            if (c == c.getCause()) break;   // 자기참조 방어
        }
        return false;
    }

    /**
     * 쿼리·경로 파라미터의 타입이 안 맞는다 — {@code DELETE /api/v1/expense/budget/abc}(QA 2026-09-03 #74).
     *
     * <p>core 가 이미 400 으로 답하고 있어 <b>상태코드는 그대로다.</b> 문제는 문구였다 — core 는
     * {@code "'%s' 파라미터의 값이 유효하지 않습니다."} 를 코드에 박아 둬서, 같은 API 가 검증 실패
     * (번들 · {@code ~어요})와 타입 불일치(하드코딩 · {@code ~습니다})에 두 말투로 답했다. 여기서
     * 가로채 번들 문구로 바꾼다. 코드는 {@code COMMON_400} 그대로다.
     *
     * <p><b>{@code BindException} 은 일부러 안 잡는다.</b> {@code MethodArgumentNotValidException} 이
     * 그 하위 타입이라, 여기에 {@code BindException} 핸들러를 두면 {@code @Valid} 실패까지 이 advice 가
     * 먼저 먹는다(Spring 은 advice 를 순서대로 훑다 처음 매칭에서 멈춘다) — DTO 마다 다른 필드 메시지가
     * 통째로 사라진다. 실제로 그렇게 되는지는
     * {@code ErrorEnvelopeTest#validationMessagesStillComeFromTheDto} 가 지킨다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("파라미터 타입 불일치: name={}, required={}", e.getName(), e.getRequiredType());

        DeskErrorCode code = DeskErrorCode.INVALID_PARAMETER_VALUE;
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), messageResolver.getMessage(code, e.getName())));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(DeskErrorCode code) {
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), messageResolver.getMessage(code)));
    }
}
