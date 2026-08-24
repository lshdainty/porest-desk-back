package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code Output_0} 이 <b>객체</b>인 응답. 시세 계열이 이 모양이다.
 *
 * <p><b>배열로 받으면 안 된다.</b> NH 스펙은 {@code Output_0} 이 API 마다 객체이거나
 * 배열이라고 명시한다 — 집계값이면 객체, 목록이면 배열이다. 우리가 쓰는 경로 중
 * 시세 2개·잔고 2개가 객체이고, 배열인 것은 계좌목록({@link NamuListEnvelope}) 하나뿐이다.
 * 객체를 {@code List} 로 선언하면 Jackson 이 {@code MismatchedInputException} 을 던지고,
 * RestTemplate 이 그걸 {@code RestClientException} 으로 감싸 "증권사 API 오류" 로 둔갑한다.
 *
 * @param output0 페이로드. 성공이어도 null 일 수 있다(조회 결과 없음)
 */
public record NamuEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") T output0
) implements NamuResponse {
}
