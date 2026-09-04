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
 * DB 무결성 제약 위반 전용 핸들러 — <b>마지막 그물</b>이다.
 *
 * <p>이름 유일성이 DB UNIQUE 로 내려가면, 조회와 저장 사이의 경쟁에서 진 요청은
 * {@link DataIntegrityViolationException} 으로 터진다. desk-back·porest-core 어디에도 이
 * 예외를 잡는 자리가 없어서 core 의 {@code @ExceptionHandler(Exception.class)} 로 떨어졌고,
 * 그러면 <b>중복을 막으려다 500 을 새로 만드는</b> 꼴이 된다.
 *
 * <p>제대로 된 답은 각 서비스가 자기 도메인 코드(예: {@code EXP_019})로 바꿔 주는 것이고
 * 이 PR 이 여덟 자리를 그렇게 고쳤다. 여기는 그 여덟이 놓친 제약 — 앞으로 붙을 UNIQUE,
 * FK, NOT NULL — 이 500 으로 새지 않게 받는 자리다. 도메인을 모르므로 문구도 도메인
 * 무관한 {@code COMMON_409}("다른 곳에서 먼저 수정됐어요") 를 쓴다.
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
        // 원인 메시지에는 위반한 인덱스 이름이 들어 있다 — 어느 제약인지 로그로 남겨야
        // "서비스가 어디를 놓쳤나" 를 나중에 추적할 수 있다.
        log.warn("무결성 제약 위반 - 서비스가 잡지 못한 중복/경쟁: {}", e.getMostSpecificCause().getMessage());

        DeskErrorCode code = DeskErrorCode.CONCURRENT_MODIFICATION;
        String message = messageResolver.getMessage(code);
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), message));
    }
}
