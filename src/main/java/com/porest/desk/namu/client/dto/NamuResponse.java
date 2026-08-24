package com.porest.desk.namu.client.dto;

/**
 * 나무증권 응답 봉투의 공통 얼굴.
 *
 * <p><b>토스와 결정적으로 다른 점</b> — 나무는 <b>HTTP 200 으로도 실패를 돌려준다.</b>
 * 성공 여부는 {@code rsp_cd} 가 {@code "00000"} 인지로만 알 수 있다. 상태코드만 보고
 * 성공으로 넘기면 실패 응답이 "조회 결과 없음" 으로 둔갑해 화면이 조용히 빈다.
 *
 * <p>봉투가 둘인 이유는 페이로드 개수다 — 조회에 따라 {@code Output_0} 하나만 오기도 하고
 * (시세), 요약({@code Output_0}) + 목록({@code Output_1})으로 나뉘어 오기도 한다(잔고).
 */
public interface NamuResponse {

    String SUCCESS = "00000";

    String rspCd();

    /** 응답메시지. 로그에만 쓴다 — 업스트림 문구를 사용자에게 릴레이하지 않는다. */
    String rspMsg();

    default boolean isSuccess() {
        return SUCCESS.equals(rspCd());
    }
}
