package com.porest.desk.subscription.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MeFeatures API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 로그인 사용자의 기능권한 + 토스 연결상태를 합쳐 응답하는지 검증한다.
 */
@WebMvcTest(controllers = MeFeaturesApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class MeFeaturesApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SubscriptionEntitlementService entitlementService;
    @MockitoBean private TossCredentialService credentialService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /users/me/features — 구독 기능권한 + 토스 연결(연결됨) 응답")
    void getMyFeatures_connected() throws Exception {
        given(entitlementService.getActiveFeatures(1L)).willReturn(List.of("SECURITIES"));
        given(credentialService.getStatus(1L))
                .willReturn(new TossCredentialService.CredentialStatus(true, true, null));

        mockMvc.perform(get("/api/v1/users/me/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.features[0]").value("SECURITIES"))
                .andExpect(jsonPath("$.data.tossConnected").value(true));

        verify(entitlementService).getActiveFeatures(1L);
        verify(credentialService).getStatus(1L);
    }

    @Test
    @DisplayName("GET /users/me/features — 비구독·미연결이면 빈 features + tossConnected=false")
    void getMyFeatures_notConnected() throws Exception {
        given(entitlementService.getActiveFeatures(1L)).willReturn(List.of());
        given(credentialService.getStatus(1L))
                .willReturn(TossCredentialService.CredentialStatus.notConnected());

        mockMvc.perform(get("/api/v1/users/me/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.features").isEmpty())
                .andExpect(jsonPath("$.data.tossConnected").value(false));

        verify(entitlementService).getActiveFeatures(1L);
        verify(credentialService).getStatus(1L);
    }
}
