package com.porest.desk.namu.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 나무증권 응답 봉투.
 *
 * <p><b>토스와 결정적으로 다른 점</b> — 나무는 <b>HTTP 200 으로도 실패를 돌려준다.</b>
 * 성공 여부는 {@code rsp_cd} 가 {@code "00000"} 인지로만 알 수 있다. 상태코드만 보고
 * 성공으로 넘기면 빈 {@code Output_0} 을 "조회 결과 없음" 으로 오인해 화면이 조용히 빈다.
 *
 * @param rspCd   응답코드. {@code "00000"} 만 정상
 * @param rspMsg  응답메시지. 로그에만 쓴다(업스트림 문구를 사용자에게 릴레이하지 않는다)
 * @param output0 페이로드. 엔드포인트에 따라 0건일 수도 있다
 */
public record NamuEnvelope<T>(
        @JsonProperty("rsp_cd") String rspCd,
        @JsonProperty("rsp_msg") String rspMsg,
        @JsonProperty("Output_0") List<T> output0
) {
    private static final String SUCCESS = "00000";

    public boolean isSuccess() {
        return SUCCESS.equals(rspCd);
    }

    public List<T> resultOrEmpty() {
        return output0 == null ? List.of() : output0;
    }
}
