package com.porest.desk.security.session.controller.dto;

import com.porest.desk.security.session.support.UserAgentParser.DeviceKind;

import java.time.LocalDateTime;

/** "로그인된 기기" 화면이 쓰는 형태. */
public final class SessionApiDto {

    private SessionApiDto() {
    }

    /**
     * @param sessionId   이 기기 세션 식별자. 로그아웃 요청에 그대로 쓴다
     * @param deviceLabel {@code iPhone · Safari}. 못 알아본 UA 면 {@code null} —
     *                    화면이 "알 수 없는 기기" 로 그린다
     * @param deviceKind  아이콘을 고르는 데만 쓴다 — 이름을 화면에서 다시 뜯지 않게
     *                    서버가 같이 내려준다
     * @param lastUsedAt  마지막 토큰 재발급 시각 [UTC]. 한 번도 재발급 안 했으면 {@code null}
     * @param createAt    로그인 시각 [UTC]
     * @param current     지금 이 요청을 보낸 기기인지
     */
    public record DeviceRes(
            String sessionId,
            String deviceLabel,
            DeviceKind deviceKind,
            LocalDateTime lastUsedAt,
            LocalDateTime createAt,
            boolean current
    ) {}
}
