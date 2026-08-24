package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code Output_0} 이 <b>배열</b>인 응답. 우리가 쓰는 경로 중에는 계좌목록
 * ({@code POST /n2/acctinfo}) 하나뿐이다.
 *
 * @param output0 목록. 성공이어도 0건일 수 있다
 */
public record NamuListEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") List<T> output0
) implements NamuResponse {

    public List<T> resultOrEmpty() {
        return output0 == null ? List.of() : output0;
    }
}
