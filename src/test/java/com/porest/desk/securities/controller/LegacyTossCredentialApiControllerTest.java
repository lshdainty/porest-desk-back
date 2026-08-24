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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구버전 앱이 쓰는 옛 토스 크리덴셜 경로.
 *
 * <p>앱이 스토어를 안 써서 구버전이 계속 돈다. 이 경로가 사라지면 토스 키를 이미 등록한
 * 사용자도 증권 화면이 "연결하세요" 로 되돌아간다. <b>옛 필드명(clientId/clientSecret)과
 * 옛 응답 모양을 그대로 지킨다</b> — 하나라도 바뀌면 구버전에서 조용히 null 이 된다.
 */
@WebMvcTest(controllers = LegacyTossCredentialApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class LegacyTossCredentialApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SecuritiesCredentialService credentialService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST — 옛 필드명(clientId/clientSecret)으로 TOSS 에 등록한다")
    void registerDelegatesToToss() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/toss-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"CID\",\"clientSecret\":\"SECRET\"}"))
                .andExpect(status().isOk());

        verify(credentialService).register(1L, SecuritiesBroker.TOSS, "CID", "SECRET");
    }

    @Test
    @DisplayName("GET — 옛 응답 모양(connected/verified/verifiedAt)을 그대로 준다")
    void statusKeepsLegacyShape() throws Exception {
        given(credentialService.getConnections(1L)).willReturn(List.of(
                new BrokerConnection(SecuritiesBroker.TOSS, "토스증권", "https://x", "Client ID",
                        "Client Secret", true, true, LocalDateTime.of(2026, 7, 1, 9, 0), true),
                BrokerConnection.notConnected(SecuritiesBroker.NAMU)));

        mockMvc.perform(get("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verifiedAt").exists())
                // 시크릿·키는 옛 응답에도 없었다. 새 필드가 섞여 나가지도 않는다.
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.data.keyLabel").doesNotExist());
    }

    @Test
    @DisplayName("GET — 나무만 연결한 사용자에게는 미연결로 보인다 (옛 앱은 토스만 안다)")
    void statusIgnoresOtherBrokers() throws Exception {
        given(credentialService.getConnections(1L)).willReturn(List.of(
                BrokerConnection.notConnected(SecuritiesBroker.TOSS),
                new BrokerConnection(SecuritiesBroker.NAMU, "나무증권", "https://y", "App Key",
                        "App Secret", true, true, null, true)));

        mockMvc.perform(get("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    @DisplayName("DELETE — TOSS 만 해제한다")
    void disconnectDelegatesToToss() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/toss-credential"))
                .andExpect(status().isOk());

        verify(credentialService).disconnect(1L, SecuritiesBroker.TOSS);
    }
}
