package com.porest.desk.apitoken.service.dto;

import com.porest.desk.apitoken.domain.UserApiToken;

import java.time.LocalDateTime;

public final class ApiTokenServiceDto {

    private ApiTokenServiceDto() {
    }

    /** 방금 발급한 토큰. {@code token} 원문은 이 응답 한 번뿐이다 — 다시 조회할 길이 없다. */
    public record IssuedToken(
        Long rowId,
        String name,
        String token,
        String tokenPrefix,
        LocalDateTime createAt
    ) {
    }

    /** 목록의 한 줄. 원문도 해시도 싣지 않는다. */
    public record TokenInfo(
        Long rowId,
        String name,
        String tokenPrefix,
        LocalDateTime createAt,
        LocalDateTime lastUsedAt
    ) {
        public static TokenInfo from(UserApiToken token) {
            return new TokenInfo(token.getRowId(), token.getName(), token.getTokenPrefix(),
                token.getCreateAt(), token.getLastUsedAt());
        }
    }

    /**
     * 토큰으로 확인된 호출자 — 인증 필터가 이것으로 로그인 사용자를 세운다.
     * 사용자 정보를 같이 싣는 이유는 {@code @LoginUser} 가 JWT 로그인과 같은 값을 받게 하려는 것이다.
     */
    public record Caller(
        Long tokenRowId,
        Long userRowId,
        String userId,
        String userName,
        String userEmail
    ) {
    }
}
