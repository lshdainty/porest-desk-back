package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 낙관적 락 충돌 전용 핸들러.
 *
 * <p>@Version 기반 낙관적 락에서 동시 수정 충돌이 나면 JPA 가
 * {@link ObjectOptimisticLockingFailureException} 을 던진다. porest-core 의
 * GlobalExceptionHandler 는 이 예외를 매핑하지 않아 그대로 500 으로 노출되므로,
 * 더 구체적인 이 핸들러에서 409(CONFLICT)로 변환해 "다시 시도" 안내를 준다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ConcurrencyExceptionHandler {

    private final MessageResolver messageResolver;

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌 - 동시 수정 감지: {}", e.getMessage());

        DeskErrorCode code = DeskErrorCode.CONCURRENT_MODIFICATION;
        String message = messageResolver.getMessage(code);
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), message));
    }
}
