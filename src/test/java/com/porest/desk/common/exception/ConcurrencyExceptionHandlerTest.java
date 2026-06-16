package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 낙관적 락 충돌이 500 이 아니라 409(CONCURRENT_MODIFICATION)로 변환되는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyExceptionHandlerTest {

    @Mock private MessageResolver messageResolver;

    @InjectMocks private ConcurrencyExceptionHandler sut;

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 CONFLICT 로 변환")
    void mapsOptimisticLockToConflict() {
        given(messageResolver.getMessage(DeskErrorCode.CONCURRENT_MODIFICATION))
                .willReturn("다른 곳에서 먼저 수정되었습니다.");

        ResponseEntity<ApiResponse<Void>> res = sut.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Expense", 1L));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_409");
    }
}
