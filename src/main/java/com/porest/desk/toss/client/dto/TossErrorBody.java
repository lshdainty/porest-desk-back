package com.porest.desk.toss.client.dto;

/**
 * 토스증권 Open API 에러 응답 envelope (4xx/5xx).<br>
 * {@code { "error": { "requestId", "code", "message", "data" } }} 형태.
 * 성공 응답({@link TossEnvelope})과 {@code result}/{@code error} 가 동시에 나타나지 않는다.
 *
 * @param error 에러 객체
 */
public record TossErrorBody(ApiError error) {

    /**
     * @param requestId 요청 식별자 (응답 헤더 {@code X-Request-Id} 와 동일). CS 문의 시 첨부 권장
     * @param code      에러 코드 (flat string, 예: {@code order-not-found}). unknown code 허용해야 함
     * @param message   사람이 읽을 수 있는 에러 메시지
     */
    public record ApiError(String requestId, String code, String message) {
    }
}
