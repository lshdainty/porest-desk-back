package com.porest.desk.security.principal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPrincipal {
    private final Long rowId;
    private final String userId;
    private final String userName;
    private final String userEmail;
    /** 이 요청이 속한 기기 세션(jti). "로그인된 기기" 목록에서 현재 기기를 가릴 때 쓴다. */
    private final String sessionId;
}
