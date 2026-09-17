package com.porest.desk.user.service;

import com.porest.core.exception.UnauthorizedException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 재인증 티켓 검증 — SSO 가 서명해 준 "방금 본인 확인이 끝났다" 를 desk 가 확인한다.
 *
 * <p>티켓 발급은 SSO 가 한다(비밀번호·메일 코드 두 경로 모두). desk 는 <b>한 형식만</b>
 * 검증하면 된다 — 그러려고 비밀번호 경로도 SSO 가 내주게 했다(sso-back#60).
 *
 * <p><b>단회 사용은 desk 가 기록한다.</b> 티켓을 실제로 쓰는 쪽이 여기뿐이라, SSO 가
 * "언제 쓰였는지" 를 알 방법이 없다. 남은 만료 시간만큼만 표식을 들고 있으면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReauthTicketVerifier {

    private static final String USED_PREFIX = "desk:reauth:used:";
    private static final String TYPE_CLAIM = "type";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String TYPE_REAUTH = "reauth";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 티켓이 이 용도로 방금 발급된 것인지 확인하고 <b>즉시 사용 처리</b>한다.
     *
     * @param ticket  {@code X-Reauth-Token} 헤더 값
     * @param purpose 이 조작의 용도(예: {@code withdraw})
     * @return 확인된 사용자 ID(SSO userId)
     * @throws UnauthorizedException 없음·서명 불일치·만료·용도 불일치·재사용
     */
    public String consume(String ticket, String purpose) {
        if (!StringUtils.hasText(ticket)) {
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.validateSsoToken(ticket);
        } catch (Exception e) {
            log.warn("재인증 티켓 검증 실패: {}", e.getMessage());
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }

        if (!TYPE_REAUTH.equals(claims.get(TYPE_CLAIM, String.class))) {
            // 접근 토큰을 재인증 티켓 자리에 넣는 걸 막는다.
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }
        if (!purpose.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            // 해지 확인으로 받은 티켓이 다른 조작에 통과하면 안 된다.
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }

        String jti = claims.getId();
        if (!StringUtils.hasText(jti)) {
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }
        Boolean firstUse = stringRedisTemplate.opsForValue()
                .setIfAbsent(USED_PREFIX + jti, "1", remainingSeconds(claims), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstUse)) {
            log.warn("재인증 티켓 재사용 시도. jti={}", jti);
            throw new UnauthorizedException(DeskErrorCode.REAUTH_REQUIRED);
        }

        return claims.getSubject();
    }

    /** 표식은 티켓이 만료될 때까지만 들고 있으면 된다 — 그 뒤엔 서명 검증이 알아서 막는다. */
    private long remainingSeconds(Claims claims) {
        long remain = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remain, 1L);
    }
}
