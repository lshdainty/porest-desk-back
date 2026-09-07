package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * DB 무결성 제약 위반 전용 핸들러 — <b>마지막 그물</b>이다. 종류를 가려서 답한다.
 *
 * <p>이름 유일성이 DB UNIQUE 로 내려가면, 조회와 저장 사이의 경쟁에서 진 요청은
 * {@link DataIntegrityViolationException} 으로 터진다. desk-back·porest-core 어디에도 이
 * 예외를 잡는 자리가 없어서 core 의 {@code @ExceptionHandler(Exception.class)} 로 떨어졌고,
 * 그러면 <b>중복을 막으려다 500 을 새로 만드는</b> 꼴이 된다. 그래서 이 자리가 생겼다(#311).
 *
 * <h4>왜 고쳤나 — QA #81</h4>
 * 처음 판은 <b>모든</b> 위반을 {@code COMMON_409}("다른 곳에서 먼저 수정됐어요") 로 포장했다.
 * 그래서 {@code eventType} 을 빼고 보낸 일정 생성처럼 <b>값이 빠진 요청</b>까지 "누가 먼저
 * 저장했다" 는 답을 받았다. 사용자에게는 재시도하면 될 것처럼 보이지만 몇 번을 보내도 같은
 * 답이 온다 — 고칠 곳이 자기 요청인데 그 사실이 답 안에 없다. 500 을 막으려던 설계가
 * <b>거짓말하는 409</b> 를 새로 만든 것이다.
 *
 * <p>이제 {@link IntegrityViolations} 로 종류를 갈라 답한다.
 * <ul>
 *   <li>{@code UNIQUE} → <b>409</b>. 이것만 진짜 경쟁이다.</li>
 *   <li>{@code NOT_NULL} → <b>400</b> "요청에 빠진 값이 있어요".</li>
 *   <li>{@code FOREIGN_KEY} → <b>400</b> "연결된 정보를 찾을 수 없어요".</li>
 *   <li>{@code OTHER}(CHECK · 길이 초과 · 판정 불가) → <b>400</b>. 제약이 걸린 이상 보낸 값이
 *       문제일 가능성이 압도적이고, 아니더라도 "남이 먼저 고쳤다" 보다는 덜 틀린다.
 *       판정이 안 됐다는 사실 자체는 아래 로그에 남는다.</li>
 * </ul>
 *
 * <p><b>도메인 문구는 여기서 만들지 않는다.</b> 여덟 도메인은 자기 UNIQUE 를 자기 코드
 * (예: {@code EXP_019})로 번역해 여기까지 오지 않는다(#311·#312 가 그 위에 서 있다).
 * 여기는 그들이 놓친 제약을 받는 자리이므로 도메인 무관한 공통 문구를 쓴다.
 *
 * <p><b>컬럼·제약 이름은 응답에 싣지 않는다</b>(QA #75). 원인 메시지에는
 * {@code "Column 'event_type' cannot be null"} 처럼 내부 이름이 그대로 들어 있고, 실측상
 * 제약 이름이 컬럼 이름 그 자체인 경우도 있다(H2 NOT NULL 위반 → {@code constraint=COLOR}).
 * 그 정보는 {@link IntegrityViolations#describe} 로 <b>로그에만</b> 남긴다 —
 * "서비스가 어디를 놓쳤나" 를 나중에 추적하려면 이 로그를 지우면 안 된다.
 *
 * <p>{@code @Order} 와 클래스를 따로 두는 이유는 {@link ConcurrencyExceptionHandler} 와 같다 —
 * core 의 {@code Exception.class} 핸들러보다 먼저 훑혀야 한다. 등록 순서에 기대면
 * 그 우연이 깨지는 날 조용히 안 불린다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class DataIntegrityExceptionHandler {

    private final MessageResolver messageResolver;

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        IntegrityViolationKind kind = IntegrityViolations.classify(e);
        DeskErrorCode code = errorCodeFor(kind);

        // 제약 이름·SQLState·벤더 코드는 여기에만 남는다(응답에는 절대 싣지 않는다).
        log.warn("무결성 제약 위반 - {} / {} → {} {}", IntegrityViolations.describe(e),
                e.getMostSpecificCause().getMessage(), code.getHttpStatus().value(), code.getCode());

        String message = messageResolver.getMessage(code);
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), message));
    }

    private static DeskErrorCode errorCodeFor(IntegrityViolationKind kind) {
        return switch (kind) {
            case UNIQUE -> DeskErrorCode.CONCURRENT_MODIFICATION;
            case NOT_NULL -> DeskErrorCode.REQUIRED_VALUE_MISSING;
            case FOREIGN_KEY -> DeskErrorCode.INVALID_REFERENCE;
            case OTHER -> DeskErrorCode.INVALID_INPUT;
        };
    }
}
