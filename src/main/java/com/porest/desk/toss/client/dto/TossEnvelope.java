package com.porest.desk.toss.client.dto;

/**
 * 토스증권 Open API 성공 응답 envelope.<br>
 * 모든 200 응답은 {@code { "result": <payload> }} 형태이며, 본 래퍼로 받은 뒤
 * {@link #result()} 만 꺼내 우리 도메인으로 노출한다.
 *
 * @param result 엔드포인트별 페이로드
 * @param <T>    페이로드 타입
 */
public record TossEnvelope<T>(T result) {
}
