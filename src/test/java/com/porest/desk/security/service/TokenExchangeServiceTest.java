package com.porest.desk.security.service;

import com.porest.desk.calendar.service.UserCalendarService;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SSO 토큰 교환(최초 프로비저닝) 회귀 방지 — 신규 사용자는 기본 캘린더를 즉시 부여하고,
 * 기존 사용자는 부여하지 않는다(중복 생성 방지).
 */
@ExtendWith(MockitoExtension.class)
class TokenExchangeServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private UserCalendarService userCalendarService;
    @Mock private com.porest.desk.security.client.SsoOAuth2Client ssoOAuth2Client;

    @InjectMocks private TokenExchangeService sut;

    private Claims deskClaims() {
        Claims c = mock(Claims.class);
        given(c.get("services", List.class)).willReturn(List.of("desk"));
        given(c.getSubject()).willReturn("tester");
        given(c.get("name", String.class)).willReturn("테스터");
        given(c.get("email", String.class)).willReturn("tester@porest.com");
        given(c.get("userNo", Long.class)).willReturn(100L);
        return c;
    }

    private User user(long rowId) {
        User u = User.createUser(100L, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    @Test
    @DisplayName("최초 로그인(신규 사용자) — 기본 캘린더를 즉시 프로비저닝한다")
    void provisionsDefaultCalendarForNewUser() {
        Claims claims = deskClaims();
        given(jwtTokenProvider.validateSsoToken("sso")).willReturn(claims);
        given(userRepository.findByUserId("tester")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(user(7L));
        given(jwtTokenProvider.createAccessToken(anyString(), anyString(), anyString(), anyLong()))
                .willReturn("access");

        sut.exchangeToken("sso");

        verify(userCalendarService).getOrCreateDefault(7L);
    }

    @Test
    @DisplayName("기존 사용자 재로그인 — 기본 캘린더를 다시 만들지 않는다")
    void doesNotProvisionForExistingUser() {
        Claims claims = deskClaims();
        given(jwtTokenProvider.validateSsoToken("sso")).willReturn(claims);
        given(userRepository.findByUserId("tester")).willReturn(Optional.of(user(7L)));
        given(jwtTokenProvider.createAccessToken(anyString(), anyString(), anyString(), anyLong()))
                .willReturn("access");

        sut.exchangeToken("sso");

        verify(userCalendarService, never()).getOrCreateDefault(eq(7L));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("exchangeCode — SSO 에 code 교환으로 ssoToken 받아 desk 토큰을 발급한다")
    void exchangeCode_exchangesViaSsoThenIssuesDeskToken() {
        given(ssoOAuth2Client.exchangeCodeForToken("authcode", "verifier", "https://desk/auth/callback"))
                .willReturn("sso");
        Claims claims = deskClaims();
        given(jwtTokenProvider.validateSsoToken("sso")).willReturn(claims);
        given(userRepository.findByUserId("tester")).willReturn(Optional.of(user(7L)));
        given(jwtTokenProvider.createAccessToken(anyString(), anyString(), anyString(), anyLong()))
                .willReturn("desk-access");

        var resp = sut.exchangeCode("authcode", "verifier", "https://desk/auth/callback");

        org.assertj.core.api.Assertions.assertThat(resp.accessToken()).isEqualTo("desk-access");
        verify(ssoOAuth2Client).exchangeCodeForToken("authcode", "verifier", "https://desk/auth/callback");
    }
}
