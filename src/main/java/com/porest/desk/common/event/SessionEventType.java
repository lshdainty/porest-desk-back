package com.porest.desk.common.event;

/** SSO 세션 이벤트 종류. 발행 쪽(porest-sso-back)의 같은 이름 enum 과 값을 맞춘다. */
public enum SessionEventType {
    /** 이 사용자의 모든 세션이 끊겼다. desk 도 자기 세션을 전부 지운다. */
    SESSION_REVOKED_ALL
}
