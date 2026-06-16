package com.porest.desk.security.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.UnauthorizedException;
import com.porest.desk.calendar.service.UserCalendarService;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.security.controller.dto.TokenExchangeDto;
import com.porest.desk.security.jwt.JwtTokenProvider;
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

    @Transactional
    public TokenExchangeDto.Response exchangeToken(String ssoToken) {
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

        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            user = userRepository.save(User.createUser(ssoUserNo, userId, userName, userEmail));
            // 신규 사용자: 기본 캘린더를 가입(최초 프로비저닝) 시점에 즉시 생성한다.
            // 지연 생성(getOrCreateDefault on first event)에 의존하면 신규 사용자에게
            // 동시 요청이 겹칠 때 기본 캘린더가 중복 생성될 수 있어, 단일 트랜잭션인
            // 이 시점에서 한 번 만들어 경쟁 자체를 없앤다.
            userCalendarService.getOrCreateDefault(user.getRowId());
        }

        // Update user info if changed
        user.updateFromSso(ssoUserNo, userName, userEmail);

        String accessToken = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getUserName(), user.getUserEmail(), user.getRowId()
        );

        return new TokenExchangeDto.Response(accessToken, user.getUserId(), user.getUserName(), user.getUserEmail());
    }
}
