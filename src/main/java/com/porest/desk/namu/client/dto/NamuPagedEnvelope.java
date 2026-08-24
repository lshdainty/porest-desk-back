package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 페이로드가 둘인 응답 — 요약({@code Output_0}) + 목록({@code Output_1}). 잔고 계열이 이 모양이다.
 *
 * <p>둘을 한 record 로 받는 이유 — 나무는 한 번의 호출로 계좌 집계와 종목별 보유를 함께 준다.
 * 나눠 부르면 두 값이 서로 다른 시점의 스냅샷이 되어 합계와 항목이 안 맞는다.
 */
public record NamuPagedEnvelope<S, I>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") List<S> output0,
        @JsonProperty("Output_1") List<I> output1
) implements NamuResponse {

    /** 요약은 1건이다. 없으면 null — 호출부가 빈 잔고로 본다. */
    public S summary() {
        return output0 == null || output0.isEmpty() ? null : output0.get(0);
    }

    public List<I> items() {
        return output1 == null ? List.of() : output1;
    }
}
