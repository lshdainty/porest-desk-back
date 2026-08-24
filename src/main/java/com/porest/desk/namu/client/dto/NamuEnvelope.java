package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 페이로드가 하나인 응답 ({@code Output_0} 만). 시세 계열이 이 모양이다.
 *
 * @param output0 페이로드. 성공이어도 0건일 수 있다(조회 결과 없음)
 */
public record NamuEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") List<T> output0
) implements NamuResponse {

    public List<T> resultOrEmpty() {
        return output0 == null ? List.of() : output0;
    }
}
