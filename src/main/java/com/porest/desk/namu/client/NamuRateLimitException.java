package com.porest.desk.namu.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.common.exception.DeskErrorCode;

/**
 * 나무가 <b>HTTP 429</b> 로 거절했다 — {@code rsp_cd=IGW42902} "APP 호출 거래건수를 초과하였습니다".
 *
 * <h2>왜 타입을 따로 두는가</h2>
 * 429 는 다른 업스트림 실패와 <b>뜻이 반대다.</b> 502·타임아웃은 "이번 호출이 실패했다" 라
 * 다른 경로로 다시 시도하는 게 맞지만, 429 는 "<b>지금 너무 많이 부르고 있다</b>" 라
 * 다시 시도하는 것 자체가 원인을 키운다.
 *
 * <p>그런데 {@link com.porest.desk.namu.client.NamuApiClient} 는 상태코드를
 * {@code ExternalServiceException} 하나로 뭉쳐 던져서 호출부가 둘을 구분할 수 없었다.
 * 실제로 환율 조회가 그 자리에서 깨졌다 — 1순위(해외 잔고)가 429 로 실패하자 곧바로
 * 2순위(해외 현재가)를 쳐서 <b>같은 초에 429 를 한 번 더</b> 맞았다
 * (dev 실측 2026-08-28: 두 경로가 30~40ms 간격으로 짝지어 8건).
 *
 * <h2>에러코드를 바꾸지 않는 이유</h2>
 * {@link DeskErrorCode#SECURITIES_API_ERROR} 를 그대로 든다. 이 타입은 <b>서버 안에서
 * 분기하려고</b> 만든 것이지 클라이언트에 새 계약을 내보내려는 게 아니다 — 코드를 바꾸면
 * 시세·캔들 응답의 HTTP 상태가 502 에서 429 로 조용히 달라지는데, 짝 레포(front·app)에
 * 동기화 장치가 없어 그 변화를 아무도 안 본다.
 */
public class NamuRateLimitException extends ExternalServiceException {

    public NamuRateLimitException(Throwable cause) {
        super(DeskErrorCode.SECURITIES_API_ERROR, cause);
    }
}
