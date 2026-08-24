package com.porest.desk.securities.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.securities.service.SecuritiesCredentialService;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 증권사 크리덴셜 API 슬라이스 테스트.
 *
 * <p>민감정보를 다루는 엔드포인트라 <b>응답에 키/시크릿이 새지 않는지</b>를 특히 본다.
 * FeatureGateInterceptor 는 슬라이스에서 미로드(ObjectProvider)라 게이트 없이 통과한다.
 */
@WebMvcTest(controllers = UserSecuritiesCredentialApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class UserSecuritiesCredentialApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SecuritiesCredentialService credentialService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST /{broker} — 증권사와 키 한 쌍을 서비스에 위임한다")
    void register() throws Exception {
        String body = """
                {"apiKey":"KEY-123","apiSecret":"SECRET-xyz"}
                """;

        mockMvc.perform(post("/api/v1/users/me/securities-credentials/NAMU")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(credentialService).register(1L, SecuritiesBroker.NAMU, "KEY-123", "SECRET-xyz");
    }

    @Test
    @DisplayName("POST — 소문자 코드도 받는다 (클라이언트 표기 흔들림 흡수)")
    void registerAcceptsLowercase() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/securities-credentials/namu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"K\",\"apiSecret\":\"S\"}"))
                .andExpect(status().isOk());

        verify(credentialService).register(1L, SecuritiesBroker.NAMU, "K", "S");
    }

    @Test
    @DisplayName("POST — 모르는 증권사는 거절한다. 조용히 기본값으로 떨어지면 엉뚱한 키로 등록된다")
    void registerRejectsUnknownBroker() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/securities-credentials/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"K\",\"apiSecret\":\"S\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET — 미연결 증권사까지 목록으로 내려가고, 키/시크릿은 응답에 없다")
    void getConnections() throws Exception {
        given(credentialService.getConnections(1L)).willReturn(List.of(
                new BrokerConnection(SecuritiesBroker.TOSS, "토스증권", "https://x", "Client ID", "Client Secret",
                        true, true, LocalDateTime.of(2026, 7, 1, 9, 0), true),
                BrokerConnection.notConnected(SecuritiesBroker.NAMU)));

        mockMvc.perform(get("/api/v1/users/me/securities-credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].broker").value("TOSS"))
                .andExpect(jsonPath("$.data[0].connected").value(true))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                .andExpect(jsonPath("$.data[1].broker").value("NAMU"))
                .andExpect(jsonPath("$.data[1].connected").value(false))
                // 화면이 폼을 그릴 수 있게 라벨은 서버가 준다 — 증권사가 늘어도 앱 배포가 필요 없다.
                .andExpect(jsonPath("$.data[1].keyLabel").value("App Key"))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].apiSecret").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /{broker} — 해제 위임")
    void disconnect() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/securities-credentials/TOSS"))
                .andExpect(status().isOk());

        verify(credentialService).disconnect(1L, SecuritiesBroker.TOSS);
    }

    @Test
    @DisplayName("PUT /{broker}/primary — 기본 시세 소스 지정 위임")
    void setPrimary() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/securities-credentials/NAMU/primary"))
                .andExpect(status().isOk());

        verify(credentialService).setPrimary(1L, SecuritiesBroker.NAMU);
    }
}
