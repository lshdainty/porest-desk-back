package com.porest.desk.security.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.security.service.TokenExchangeService;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TokenExchangeController — embed-token 엔드포인트 슬라이스 테스트.
 * 인증된 사용자(@WithLoginUser) → JwtTokenProvider.createEmbedToken 호출 + 응답 본문 검증.
 */
@WebMvcTest(controllers = TokenExchangeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 7L, userId = "user7", userName = "User7", userEmail = "u7@e.com")
class TokenExchangeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TokenExchangeService tokenExchangeService;
    @MockitoBean private JwtProperties jwtProperties;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private MessageResolver messageResolver;
    @MockitoBean private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/auth/embed-token — 로그인 사용자 정보로 createEmbedToken 호출 + 60초 만료 응답")
    void issueEmbedToken_callsProviderWithLoginUser() throws Exception {
        given(jwtTokenProvider.createEmbedToken(eq("user7"), eq("User7"), eq("u7@e.com"), eq(7L)))
                .willReturn("EMBED_JWT_TOKEN");

        mockMvc.perform(post("/api/v1/auth/embed-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("EMBED_JWT_TOKEN"))
                .andExpect(jsonPath("$.data.expiresIn").value(60));

        verify(jwtTokenProvider).createEmbedToken("user7", "User7", "u7@e.com", 7L);
    }

    @Test
    @DisplayName("GET /api/v1/auth/check — 로그인 사용자 정보 + 가입일시(joinedAt=User.createAt) 응답")
    void checkLogin_returnsUserInfoWithJoinedAt() throws Exception {
        User user = mock(User.class);
        given(user.getCreateAt()).willReturn(LocalDateTime.of(2024, 11, 5, 9, 30));
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/auth/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(7))
                .andExpect(jsonPath("$.data.userId").value("user7"))
                .andExpect(jsonPath("$.data.userName").value("User7"))
                .andExpect(jsonPath("$.data.userEmail").value("u7@e.com"))
                .andExpect(jsonPath("$.data.joinedAt").exists());

        verify(userRepository).findById(7L);
    }

    @Test
    @DisplayName("GET /api/v1/auth/check — 유저 미조회 시 joinedAt=null (그 외 필드 정상 응답)")
    void checkLogin_userNotFound_joinedAtNull() throws Exception {
        given(userRepository.findById(7L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/auth/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user7"))
                .andExpect(jsonPath("$.data.joinedAt").value(nullValue()));
    }
}
