package com.porest.desk.apitoken.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.apitoken.service.ApiTokenService;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.IssuedToken;
import com.porest.desk.apitoken.service.dto.ApiTokenServiceDto.TokenInfo;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 토큰 발급·목록·폐기 슬라이스 테스트.
 *
 * <p><b>원문이 나가는 자리는 발급 응답 하나뿐</b>인지를 본다. 목록에 원문이나 해시가 실리면
 * 화면을 연 것만으로 토큰이 다시 노출된다.
 * FeatureGateInterceptor 는 슬라이스에서 미로드(ObjectProvider)라 게이트 없이 통과한다.
 */
@WebMvcTest(controllers = UserApiTokenApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class UserApiTokenApiControllerTest {

    private static final String RAW = "pdk_0123456789abcdefghijklmnopqrstuvwxyzABCDEFG";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 30, 3, 0, 0);

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ApiTokenService apiTokenService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST — 로그인한 사람 이름으로 발급하고 원문을 한 번 돌려준다")
    void issue() throws Exception {
        given(apiTokenService.issue(1L, "보고서 차트"))
            .willReturn(new IssuedToken(9L, "보고서 차트", RAW, "pdk_01234567", CREATED));

        mockMvc.perform(post("/api/v1/users/me/api-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"보고서 차트"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rowId").value(9))
                .andExpect(jsonPath("$.data.name").value("보고서 차트"))
                .andExpect(jsonPath("$.data.token").value(RAW))
                .andExpect(jsonPath("$.data.tokenPrefix").value("pdk_01234567"));

        verify(apiTokenService).issue(1L, "보고서 차트");
    }

    @Test
    @DisplayName("GET — 목록에는 원문이 없다. 앞부분과 시각만 나간다")
    void list() throws Exception {
        given(apiTokenService.getTokens(1L)).willReturn(List.of(
            new TokenInfo(9L, "보고서 차트", "pdk_01234567", CREATED, CREATED.plusHours(1)),
            new TokenInfo(8L, "안 쓴 토큰", "pdk_abcdefgh", CREATED, null)));

        mockMvc.perform(get("/api/v1/users/me/api-tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rowId").value(9))
                .andExpect(jsonPath("$.data[0].tokenPrefix").value("pdk_01234567"))
                .andExpect(jsonPath("$.data[0].lastUsedAt").exists())
                .andExpect(jsonPath("$.data[1].lastUsedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].token").doesNotExist())
                .andExpect(jsonPath("$.data[0].tokenHash").doesNotExist())
                .andExpect(content().string(not(containsString(RAW))));
    }

    @Test
    @DisplayName("DELETE — 로그인한 사람의 토큰으로 폐기를 위임한다")
    void revoke() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/api-tokens/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiTokenService).revoke(1L, 9L);
    }
}
