package io.groo.damda.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 나무 응답의 {@code message} 봉투 — 2026-10-15 문서 개정 이후 quote · inquiry 엔드포인트의
 * 응답 칸은 top-level {@code rsp_cd} · {@code rsp_msg} 대신 이 블록({@code msg_code} ·
 * {@code usr_msg})으로 적혀 있다. 실측 메모와 테스트 픽스처는 아직 top-level 이라 <b>둘 다 읽는다.</b>
 *
 * @param code    {@code msg_code}
 * @param message {@code usr_msg}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamuMessage(
        @JsonProperty("msg_code") String code,
        @JsonProperty("usr_msg") String message
) {
}
