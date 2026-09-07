package com.porest.desk.common.exception;

import com.porest.core.controller.ApiResponse;
import com.porest.core.util.MessageResolver;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 마지막 그물이 <b>종류대로</b> 답하는지 — QA #81 의 회귀 기준.
 *
 * <p>이 핸들러가 없던 동안 {@link DataIntegrityViolationException} 은 core 의
 * {@code Exception.class} 핸들러로 떨어져 500 이 됐다(#311 이 그걸 막았다).
 * 그런데 막는 김에 <b>전부</b> 409 로 포장해서, 값을 빼먹은 요청까지 "다른 곳에서 먼저
 * 수정됐어요" 를 받았다. 아래 네 개가 그 넷을 갈라 둔다.
 */
@ExtendWith(MockitoExtension.class)
class DataIntegrityExceptionHandlerTest {

    @Mock private MessageResolver messageResolver;

    @InjectMocks private DataIntegrityExceptionHandler sut;

    private static DataIntegrityViolationException violation(
            ConstraintViolationException.ConstraintKind kind, String sqlState, String constraint) {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "h2", sqlState, Integer.parseInt(sqlState));
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, kind, constraint));
    }

    private ResponseEntity<ApiResponse<Void>> handle(DataIntegrityViolationException e, DeskErrorCode expected) {
        given(messageResolver.getMessage(expected)).willReturn("문구");
        return sut.handleDataIntegrityViolation(e);
    }

    @Test
    @DisplayName("UNIQUE 위반만 409 — 진짜 경쟁에서 진 경우")
    void uniqueStaysConflict() {
        ResponseEntity<ApiResponse<Void>> res = handle(
                violation(ConstraintViolationException.ConstraintKind.UNIQUE, "23505", "UK_TODO_TAG"),
                DeskErrorCode.CONCURRENT_MODIFICATION);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_409");
    }

    @Test
    @DisplayName("NOT NULL 위반은 400 — QA #81 이 신고한 그 자리(eventType 누락)")
    void notNullBecomesBadRequest() {
        ResponseEntity<ApiResponse<Void>> res = handle(
                violation(ConstraintViolationException.ConstraintKind.NOT_NULL, "23502", "EVENT_TYPE"),
                DeskErrorCode.REQUIRED_VALUE_MISSING);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_400");
    }

    @Test
    @DisplayName("FK 위반도 400 — 없는 것을 가리킨 요청이지 경쟁이 아니다")
    void foreignKeyBecomesBadRequest() {
        ResponseEntity<ApiResponse<Void>> res = handle(
                violation(ConstraintViolationException.ConstraintKind.FOREIGN_KEY, "23506", "FK_EVENT"),
                DeskErrorCode.INVALID_REFERENCE);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo("COMMON_400");
    }

    @Test
    @DisplayName("판정 못 한 위반도 400 — 409 는 UNIQUE 라고 확인됐을 때만 쓴다")
    void unknownBecomesBadRequest() {
        ResponseEntity<ApiResponse<Void>> res = handle(
                new DataIntegrityViolationException("UK_event_label_user_active_name"),
                DeskErrorCode.INVALID_INPUT);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("응답에 컬럼·제약 이름이 새지 않는다 (QA #75)")
    void neverLeaksColumnNames() {
        given(messageResolver.getMessage(DeskErrorCode.REQUIRED_VALUE_MISSING))
                .willReturn("요청에 빠진 값이 있어요. 입력을 확인해 주세요");

        ResponseEntity<ApiResponse<Void>> res = sut.handleDataIntegrityViolation(
                violation(ConstraintViolationException.ConstraintKind.NOT_NULL, "23502", "EVENT_TYPE"));

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage())
                .doesNotContainIgnoringCase("event_type")
                .doesNotContainIgnoringCase("eventType")
                .doesNotContainIgnoringCase("column")
                .doesNotContain("23502");
    }
}
