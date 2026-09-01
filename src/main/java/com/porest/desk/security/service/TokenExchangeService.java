package com.porest.desk.security.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.UnauthorizedException;
import com.porest.desk.calendar.service.UserCalendarService;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.client.SsoOAuth2Client;
import com.porest.desk.security.controller.dto.TokenExchangeDto;
import com.porest.desk.expense.service.ExpenseCategoryService;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.principal.JwtClaimsPrincipal;
import com.porest.desk.security.session.service.SsoSessionService;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {
    private static final String DESK_SERVICE_CODE = "desk";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserCalendarService userCalendarService;
    private final ExpenseCategoryService expenseCategoryService;
    private final SsoOAuth2Client ssoOAuth2Client;
    private final SsoSessionService ssoSessionService;

    /**
     * Authorization Code 교환 — SSO {@code /oauth2/token} 에 code+code_verifier 를 교환해
     * SSO access_token 을 받은 뒤, 기존 {@link #exchangeToken} 경로로 검증·자체 desk JWT 발급한다.
     * (BFF: 자체 desk JWT 를 httpOnly 쿠키로 내려준다.)
     *
     * <p>같이 받은 refresh token 은 세션에 보관한다. desk 토큰이 만료됐을 때 사용자를 다시
     * 로그인시키지 않고 조용히 재발급하는 데 쓴다.
     */
    @Transactional
    public TokenExchangeDto.Response exchangeCode(String code, String codeVerifier, String redirectUri,
                                                  String userAgent) {
        SsoOAuth2Client.TokenPair pair = ssoOAuth2Client.exchangeCodeForToken(code, codeVerifier, redirectUri);
        String sessionId = jwtTokenProvider.newSessionId();
        Exchanged exchanged = doExchange(pair.accessToken(), sessionId);
        ssoSessionService.create(exchanged.userRowId(), sessionId, pair.refreshToken(), userAgent);
        return exchanged.response();
    }

    /**
     * 만료된 desk 토큰을 조용히 새 것으로 바꾼다 — 필터가 부른다.
     *
     * <p>재발급이 되지 않으면 {@code null}. 그러면 요청은 인증 없이 흘러가 401 이 되고,
     * 클라이언트가 로그인 화면으로 보낸다.
     *
     * @param expired 서명은 검증됐고 만료만 된 토큰의 claims
     */
    @Transactional
    public String reauthenticate(JwtClaimsPrincipal expired) {
        SsoSessionService.RefreshResult result = ssoSessionService.refresh(expired.sessionId());
        if (!result.renewed()) {
            return null;
        }

        if (result.ssoAccessToken() != null) {
            // SSO 를 실제로 불렀다 — 로그인과 같은 경로로 태운다. desk 접근 권한·사용자 정보가
            // 매 재발급마다 다시 확인된다(권한을 뺏겼으면 여기서 걸린다).
            return exchangeToken(result.ssoAccessToken(), expired.sessionId()).accessToken();
        }

        // 앞선 요청이 방금 갱신해 뒀다. 신원은 서명이 검증된 만료 토큰에서 그대로 가져온다.
        return jwtTokenProvider.createAccessToken(
            expired.userId(), expired.userName(), expired.userEmail(), expired.userRowId(), expired.sessionId()
        );
    }

    @Transactional
    public TokenExchangeDto.Response exchangeToken(String ssoToken, String sessionId) {
        return doExchange(ssoToken, sessionId).response();
    }

    /**
     * 응답과 함께 userRowId 를 들고 나온다 — 세션을 만들 때 필요한데, API 응답 형태에
     * 내부 식별자를 끼워 넣고 싶지 않아 안쪽에서만 쓴다.
     */
    private record Exchanged(TokenExchangeDto.Response response, Long userRowId) {}

    private Exchanged doExchange(String ssoToken, String sessionId) {
        Claims ssoClaims;
        try {
            ssoClaims = jwtTokenProvider.validateSsoToken(ssoToken);
        } catch (Exception e) {
            log.error("SSO token validation failed: {}", e.getMessage());
            throw new UnauthorizedException(DeskErrorCode.AUTH_EXCHANGE_FAILED);
        }

        // services claim에서 desk 서비스 접근 권한 확인
        List<String> services = ssoClaims.get("services", List.class);
        if (services == null || !services.contains(DESK_SERVICE_CODE)) {
            log.warn("User does not have access to Desk service");
            throw new ForbiddenException(DeskErrorCode.AUTH_ACCESS_DENIED);
        }

        String userId = ssoClaims.getSubject();
        String userName = ssoClaims.get("name", String.class);
        String userEmail = ssoClaims.get("email", String.class);
        Long ssoUserNo = ssoClaims.get("userNo", Long.class);
        // 가입 지역 — SSO 가 회원가입 때 받아 claim 으로 내려준다.
        // 구버전 토큰에는 없으므로 null 이면 User 기본값(Asia/Seoul)이 쓰인다.
        String timezone = ssoClaims.get("timezone", String.class);

        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            user = userRepository.save(User.createUser(ssoUserNo, userId, userName, userEmail, timezone));
            // 신규 사용자: 기본 캘린더를 가입(최초 프로비저닝) 시점에 즉시 생성한다.
            // 지연 생성(getOrCreateDefault on first event)에 의존하면 신규 사용자에게
            // 동시 요청이 겹칠 때 기본 캘린더가 중복 생성될 수 있어, 단일 트랜잭션인
            // 이 시점에서 한 번 만들어 경쟁 자체를 없앤다.
            userCalendarService.getOrCreateDefault(user.getRowId());
            // 기본 카테고리도 같은 이유로 이 시점에 심는다 — 카테고리가 하나도
            // 없으면 거래 시트의 저장이 조용히 비활성이라 첫 기록 자체가 막힌다.
            expenseCategoryService.seedDefaults(user.getRowId());
        }

        // Update user info if changed
        user.updateFromSso(ssoUserNo, userName, userEmail);

        String accessToken = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getUserName(), user.getUserEmail(), user.getRowId(), sessionId
        );

        return new Exchanged(
            new TokenExchangeDto.Response(accessToken, user.getUserId(), user.getUserName(), user.getUserEmail()),
            user.getRowId()
        );
    }
}
