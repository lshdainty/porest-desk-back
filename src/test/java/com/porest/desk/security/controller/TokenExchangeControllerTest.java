package com.porest.desk.security.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.properties.JwtProperties;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.security.service.TokenExchangeService;
import com.porest.desk.support.security.WithLoginUser;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
}
