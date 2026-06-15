package com.porest.desk.asset.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.asset.service.AssetService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asset API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = AssetApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class AssetApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AssetService assetService;
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /assets — 로그인 사용자로 목록 조회")
    void getAssets() throws Exception {
        given(assetService.getAssets(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isOk());

        verify(assetService).getAssets(1L);
    }

    @Test
    @DisplayName("DELETE /asset/{id} — id·로그인 사용자로 삭제 위임")
    void deleteAsset() throws Exception {
        mockMvc.perform(delete("/api/v1/asset/{id}", 100L))
                .andExpect(status().isOk());

        verify(assetService).deleteAsset(eq(100L), eq(1L));
    }
}
