package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
 *
 * <p>{@code @Order} 는 core 의 {@code @ExceptionHandler(Exception.class)} 보다 먼저 훑히기 위한 것이다.
 * 종전엔 {@code scanBasePackages} 등록 순서 덕에 우연히 이겼는데(둘 다 LOWEST_PRECEDENCE 동률),
 * 그 우연이 깨지면 이 핸들러는 조용히 안 불린다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
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
