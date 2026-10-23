package io.groo.damda.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code Output_0} 이 <b>배열</b>인 응답 — 계좌목록 · 체결 · 증거금 · 권리예정 · ETF 구성종목.
 *
 * @param output0 목록. 성공이어도 0건일 수 있다
 * @param message 문서 개정 이후의 {@code message} 봉투. 없으면 null
 */
public record NamuListEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") List<T> output0,
        @JsonProperty("message") NamuMessage message
) implements NamuResponse {

    public List<T> resultOrEmpty() {
        return output0 == null ? List.of() : output0;
    }

    @Override
    public boolean hasPayload() {
        return output0 != null && !output0.isEmpty();
    }
}
