package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * DB UNIQUE 위반이 500 이 아니라 409 로 나가는지 — 마지막 그물의 회귀 기준.
 *
 * <p>이 핸들러가 없던 동안 desk-back·porest-core 어디에도 {@link DataIntegrityViolationException}
 * 을 잡는 자리가 없어 core 의 {@code Exception.class} 핸들러로 떨어졌다.
 */
@ExtendWith(MockitoExtension.class)
class DataIntegrityExceptionHandlerTest {

    @Mock private MessageResolver messageResolver;

    @InjectMocks private DataIntegrityExceptionHandler sut;

    @Test
    @DisplayName("DataIntegrityViolationException → 409 CONFLICT 로 변환")
    void mapsConstraintViolationToConflict() {
        given(messageResolver.getMessage(DeskErrorCode.CONCURRENT_MODIFICATION))
                .willReturn("다른 곳에서 먼저 수정됐어요.");

        ResponseEntity<ApiResponse<Void>> res = sut.handleDataIntegrityViolation(
                new DataIntegrityViolationException("UK_event_label_user_active_name"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_409");
    }
}
