package com.porest.desk.user.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.user.controller.dto.UserApiDto.PreferencesResponse;
import com.porest.desk.user.controller.dto.UserApiDto.UpdatePreferencesReq;
import com.porest.desk.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserApiController 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 비밀번호 API 는 SSO userId 로, 환경설정 API 는 rowId 로 위임하므로 두 값 모두 검증한다.
 * 서비스는 mock — 매핑·바디 역직렬화·로그인 사용자 위임·@Valid 검증을 확인한다.
 */
@WebMvcTest(controllers = UserApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L, userId = "user1")
class UserApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private PreferencesResponse samplePreferences() {
        return new PreferencesResponse(
                true, false, false, false, false, false, false, false,
                80, false, null, null, "DEFAULT", true, false, "WEEKLY", "Asia/Seoul");
    }

    @Test
    @DisplayName("PATCH /users/me/password — 로그인 userId·바디로 changePassword 위임")
    void changePassword() throws Exception {
        String body = """
                {"currentPassword":"oldpw123","newPassword":"newpw1234","confirmPassword":"newpw1234"}
                """;

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).changePassword("user1", "oldpw123", "newpw1234", "newpw1234");
    }

    @Test
    @DisplayName("PATCH /users/me/password — 비밀번호 정책은 SSO 소유: 짧은 값도 판단하지 않고 위임")
    void changePassword_policyIsOwnedBySso() throws Exception {
        // desk-back 은 비밀번호를 소유하지 않는 프록시다. 여기서 길이·문자 규칙을 함께 들고 있으면
        // SSO 가 정책을 바꿨을 때 조용히 어긋난다(= 여기서만 거부되는 값이 생긴다).
        // 채워졌는지(@NotBlank)만 보고 판단은 SSO 에 맡긴다.
        String body = """
                {"currentPassword":"oldpw123","newPassword":"short","confirmPassword":"short"}
                """;

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(userService).changePassword("user1", "oldpw123", "short", "short");
    }

    @Test
    @DisplayName("PATCH /users/me/password — newPassword 가 비면 400")
    void changePassword_blank_returns400() throws Exception {
        String body = """
                {"currentPassword":"oldpw123","newPassword":"","confirmPassword":""}
                """;

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /users/me/verify-password — 로그인 userId·비밀번호로 verifyPassword 위임")
    void verifyPassword() throws Exception {
        String body = """
                {"password":"mypw12345"}
                """;

        mockMvc.perform(post("/api/v1/users/me/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).verifyPassword("user1", "mypw12345");
    }

    @Test
    @DisplayName("POST /users/me/verify-password — 비밀번호 공백이면 400")
    void verifyPassword_blank_returns400() throws Exception {
        String body = """
                {"password":""}
                """;

        mockMvc.perform(post("/api/v1/users/me/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /users/me/preferences — 로그인 rowId 로 환경설정 조회")
    void getPreferences() throws Exception {
        given(userService.getPreferences(1L)).willReturn(samplePreferences());

        mockMvc.perform(get("/api/v1/users/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(true))
                .andExpect(jsonPath("$.data.budgetAlertThreshold").value(80))
                .andExpect(jsonPath("$.data.emailFrequency").value("WEEKLY"));

        verify(userService).getPreferences(1L);
    }

    @Test
    @DisplayName("PATCH /users/me/preferences — 로그인 rowId·바디로 부분 수정 위임")
    void updatePreferences() throws Exception {
        given(userService.updatePreferences(eq(1L), any(UpdatePreferencesReq.class)))
                .willReturn(samplePreferences());

        String body = """
                {"pushEnabled":false,"budgetAlertThreshold":90,"emailFrequency":"DAILY"}
                """;

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(true));

        ArgumentCaptor<UpdatePreferencesReq> captor = ArgumentCaptor.forClass(UpdatePreferencesReq.class);
        verify(userService).updatePreferences(eq(1L), captor.capture());
        assertThat(captor.getValue().getPushEnabled()).isFalse();
        assertThat(captor.getValue().getBudgetAlertThreshold()).isEqualTo(90);
        assertThat(captor.getValue().getEmailFrequency()).isEqualTo("DAILY");
    }

    @Test
    @DisplayName("PATCH /users/me/preferences — budgetAlertThreshold 범위 초과면 400")
    void updatePreferences_thresholdOutOfRange_returns400() throws Exception {
        String body = """
                {"budgetAlertThreshold":200}
                """;

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
