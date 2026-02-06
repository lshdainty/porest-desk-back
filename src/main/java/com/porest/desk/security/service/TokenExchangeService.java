package com.porest.desk.security.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {
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

        String userId = ssoClaims.getSubject();
        String userName = ssoClaims.get("userName", String.class);
        String userEmail = ssoClaims.get("userEmail", String.class);

        User user = userRepository.findByUserId(userId)
            .orElseGet(() -> {
                User newUser = User.createUser(userId, userName, userEmail);
                return userRepository.save(newUser);
            });

        // Update user info if changed
        user.updateFromSso(userName, userEmail);

        String accessToken = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getUserName(), user.getUserEmail(), user.getRowId()
        );

        return new TokenExchangeDto.Response(accessToken, user.getUserId(), user.getUserName(), user.getUserEmail());
    }
}
