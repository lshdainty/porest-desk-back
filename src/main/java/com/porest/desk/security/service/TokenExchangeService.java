package com.porest.desk.security.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.UnauthorizedException;
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

        User user = userRepository.findByUserId(userId)
            .orElseGet(() -> {
                User newUser = User.createUser(ssoUserNo, userId, userName, userEmail);
                return userRepository.save(newUser);
            });

        // Update user info if changed
        user.updateFromSso(ssoUserNo, userName, userEmail);

        String accessToken = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getUserName(), user.getUserEmail(), user.getRowId()
        );

        return new TokenExchangeDto.Response(accessToken, user.getUserId(), user.getUserName(), user.getUserEmail());
    }
}
