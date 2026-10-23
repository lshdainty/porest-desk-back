package io.groo.damda.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code Output_0} 이 <b>객체인지 배열인지 문서가 확정하지 못한</b> 응답 — 기간별 시세 머리(EPS).
 * {@code Object} 로 받아 {@code Map} 이면 그대로, {@code List} 면 첫 원소를 쓴다.
 * ({@code JsonNode} 는 Jackson 3 컨버터가 못 읽는다 — {@link NamuCandleEnvelope} 주석.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamuLooseEnvelope(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") Object output0,
        @JsonProperty("message") NamuMessage message
) implements NamuResponse {

    @Override
    public boolean hasPayload() {
        return output0 != null;
    }
}
