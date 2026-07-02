package com.porest.desk.toss.credential.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.toss.credential.service.TossCredentialService;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserTossCredentialApiController(사용자 토스 크리덴셜) 슬라이스 테스트.
 *
 * <p>민감정보(client_id/secret) 취급 — 서비스는 mock 으로 격리하고, 등록/상태/해제의 rowId 위임과
 * 응답에 secret/clientId 평문이 노출되지 않는지(상태 응답 필드) 검증한다.
 * FeatureGateInterceptor 는 슬라이스에서 미로드(ObjectProvider)라 게이트 없이 통과한다.
 */
@WebMvcTest(controllers = UserTossCredentialApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class UserTossCredentialApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TossCredentialService credentialService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST /users/me/toss-credential — rowId·clientId·clientSecret 로 등록 위임")
    void register() throws Exception {
        String body = """
                {"clientId":"CID-123","clientSecret":"SECRET-xyz"}
                """;

        mockMvc.perform(post("/api/v1/users/me/toss-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(credentialService).register(1L, "CID-123", "SECRET-xyz");
    }

    @Test
    @DisplayName("GET /users/me/toss-credential — 상태 조회, secret/clientId 평문 미노출")
    void getStatus_connected() throws Exception {
        given(credentialService.getStatus(1L)).willReturn(
                new TossCredentialService.CredentialStatus(true, true, LocalDateTime.of(2026, 7, 1, 9, 0)));

        mockMvc.perform(get("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verifiedAt").exists())
                .andExpect(jsonPath("$.data.clientId").doesNotExist())
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());

        verify(credentialService).getStatus(1L);
    }

    @Test
    @DisplayName("GET /users/me/toss-credential — 미연결이면 connected=false")
    void getStatus_notConnected() throws Exception {
        given(credentialService.getStatus(1L)).willReturn(
                TossCredentialService.CredentialStatus.notConnected());

        mockMvc.perform(get("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.verified").value(false));

        verify(credentialService).getStatus(1L);
    }

    @Test
    @DisplayName("DELETE /users/me/toss-credential — rowId 로 해제 위임")
    void disconnect() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(credentialService).disconnect(1L);
    }
}
