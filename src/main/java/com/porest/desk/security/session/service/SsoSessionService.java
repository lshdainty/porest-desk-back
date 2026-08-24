package com.porest.desk.security.session.service;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.security.client.SsoOAuth2Client;
import com.porest.desk.security.session.domain.UserSsoSession;
import com.porest.desk.security.session.repository.UserSsoSessionRepository;
import com.porest.desk.security.session.support.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 기기별 SSO 세션 관리 — 로그인 때 refresh token 을 받아 두었다가, desk access token 이
 * 만료되면 사용자 개입 없이 SSO 에 재발급을 요청한다(OAuth BFF).
 *
 * <p>refresh token 은 AES-GCM 암호문으로만 둔다. 해시가 아닌 이유는 SSO 로 재발급을 요청할 때
 * 원본이 필요해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoSessionService {

    /**
     * 앞선 요청이 갱신한 직후로 보는 시간(초).
     *
     * <p>토큰이 만료된 순간 여러 요청이 한꺼번에 들어온다. 락으로 줄을 세워도, 뒤따라온 요청이
     * SSO 를 또 부르면 rotation 때문에 방금 받은 토큰이 폐기된다. 이 시간 안에 갱신된 세션은
     * 다시 부르지 않고 그대로 쓴다. 요청 하나가 도는 시간(SSO read timeout 10초)보다 넉넉해야
     * 뒤따라온 요청이 창을 놓치지 않는다.
     */
    private static final long REFRESH_GRACE_SECONDS = 30L;


    private final UserSsoSessionRepository sessionRepository;
    private final SsoOAuth2Client ssoOAuth2Client;
    private final AesGcmCipher cipher;
    private final JwtProperties jwtProperties;

    /**
     * 재발급 결과.
     *
     * @param renewed        세션이 살아 있어 desk 토큰을 새로 내줘도 되는지
     * @param ssoAccessToken SSO 를 실제로 불러 받아온 새 access token. 앞선 요청이 방금 갱신해
     *                       둔 경우에는 {@code null} — 세션은 멀쩡하니 desk 토큰만 새로 만들면 된다.
     */
    public record RefreshResult(boolean renewed, String ssoAccessToken) {
        static RefreshResult failed() { return new RefreshResult(false, null); }
        static RefreshResult reusedExisting() { return new RefreshResult(true, null); }
        static RefreshResult fromSso(String ssoAccessToken) { return new RefreshResult(true, ssoAccessToken); }
    }

    /**
     * 로그인 직후 세션 생성.
     *
     * <p>세션을 못 만들 사정이면 만들지 않고 넘어간다 — 무음 재인증만 안 되고 로그인은 된다.
     * 여기서 예외를 던지면 로그인 트랜잭션이 통째로 깨져, 부가 기능 하나 때문에 아무도
     * 못 들어오게 된다.
     */
    @Transactional
    public void create(Long userRowId, String sessionId, String refreshToken, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("SSO 가 refresh token 을 주지 않아 세션을 만들지 않는다. userRowId={}", userRowId);
            return;
        }
        if (!cipher.isConfigured()) {
            // 암호화 키 없이 평문으로 저장하는 선택지는 없다. 키를 채우면 그때부터 동작한다.
            log.error("app.security.encryption-key 미설정 — SSO 세션을 만들지 않는다."
                    + " 무음 재인증이 동작하지 않아 access token 만료마다 재로그인하게 된다. userRowId={}", userRowId);
            return;
        }
        sessionRepository.save(UserSsoSession.issue(
                userRowId, sessionId, cipher.encrypt(refreshToken), sessionExpiry(now()), deviceLabel(userAgent)));
    }

    /**
     * 만료된 세션 재발급.
     *
     * <p>실패를 두 갈래로 나눈다. 뭉뚱그리면 SSO 가 잠깐 죽었을 때 전체 사용자가 로그아웃된다.
     * <ul>
     *   <li>SSO 가 거부(4xx) → 세션을 끊는다. 다시 시도해도 결과가 같으니 사용자에게 로그인을 시킨다.</li>
     *   <li>SSO 가 응답 못 함(5xx·타임아웃) → 세션은 그대로 둔다. 이번 요청만 401 이고 다음에 다시 시도된다.</li>
     * </ul>
     */
    @Transactional
    public RefreshResult refresh(String sessionId) {
        UserSsoSession session = sessionRepository.findForRefresh(sessionId, YNType.N).orElse(null);
        if (session == null) {
            return RefreshResult.failed();
        }

        LocalDateTime now = now();
        if (session.isExpired(now)) {
            // 어차피 SSO 가 거부한다. 부르지 않고 여기서 끊는다.
            log.info("SSO 세션 만료 — 재로그인 필요. sessionId={}", sessionId);
            session.revoke();
            return RefreshResult.failed();
        }

        // 락을 잡고 보니 앞선 요청이 이미 갱신해 뒀다. 또 부르면 방금 받은 토큰이 폐기된다.
        if (session.refreshedWithin(now, REFRESH_GRACE_SECONDS)) {
            return RefreshResult.reusedExisting();
        }

        SsoOAuth2Client.TokenPair pair;
        try {
            pair = ssoOAuth2Client.refreshTokens(cipher.decrypt(session.getRefreshTokenEnc()));
        } catch (Exception e) {
            // 일시적 장애 — 세션을 끊지 않는다. 여기서 끊으면 SSO 재시작 한 번에 전원이 로그아웃된다.
            log.error("SSO 재발급 호출 실패(일시적으로 본다). sessionId={}, err={}", sessionId, e.getMessage());
            return RefreshResult.failed();
        }

        if (pair == null) {
            // SSO 가 명시적으로 거부했다 — 만료·폐기·권한 회수. 확정이므로 세션을 끊는다.
            log.info("SSO 가 재발급을 거부 — 세션 폐기. sessionId={}", sessionId);
            session.revoke();
            return RefreshResult.failed();
        }

        if (!pair.hasRefreshToken()) {
            // rotation 이 오지 않았다. 옛 토큰은 이미 폐기됐을 수 있어 다음 재발급을 보장할 수 없다.
            log.error("SSO 재발급 응답에 refresh token 이 없다 — 세션 폐기. sessionId={}", sessionId);
            session.revoke();
            return RefreshResult.failed();
        }

        session.rotate(cipher.encrypt(pair.refreshToken()), sessionExpiry(now), now);
        return RefreshResult.fromSso(pair.accessToken());
    }

    /** 로그아웃 — 이 기기 세션만 끊는다. 다른 기기는 살아 있어야 한다. */
    @Transactional
    public void revoke(String sessionId) {
        sessionRepository.findBySessionIdAndIsDeleted(sessionId, YNType.N)
                .ifPresent(UserSsoSession::revoke);
    }

    /**
     * 새 refresh 를 받을 때마다 만료를 미룬다(sliding).
     *
     * <p>SSO 가 rotation 으로 매번 수명을 새로 주므로, 계속 쓰는 사용자는 로그아웃되지 않는다.
     * 반대로 7일 넘게 안 들어오면 세션이 끝난다.
     */
    private LocalDateTime sessionExpiry(LocalDateTime now) {
        return now.plusSeconds(jwtProperties.getSessionExpiration() / 1000);
    }

    /** [UTC] 저장·비교는 UTC — 표시할 때만 사용자 타임존으로 바꾼다. */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * "로그인된 기기" 목록에 쓸 이름.
     *
     * <p>예전에는 UA 원문을 잘라 담았다. 사용자에게
     * {@code Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) ...} 를 보여주는 셈이라
     * 내 기기인지 알아볼 수가 없었다. 이제 {@code iPhone · Safari} 로 줄인다.
     *
     * <p>못 알아본 UA 는 {@code null} — 화면이 "알 수 없는 기기" 로 표시한다.
     */
    private String deviceLabel(String userAgent) {
        return UserAgentParser.parse(userAgent);
    }
}
