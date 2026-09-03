package com.porest.desk.asset.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.core.type.YNType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private AssetServiceDto.AssetInfo sampleAsset() {
        return new AssetServiceDto.AssetInfo(
                100L, 1L, "주거래", AssetType.BANK_ACCOUNT, 50_000L, 50_000L, 0L, "KRW",
                java.math.BigDecimal.ONE,        // exchangeRate
                null, null, null, 0, YNType.Y,   // color · institution · memo · sortOrder · isIncludedInTotal
                null, null, null, null,          // cardCatalog · creditLimit · paymentDay · paymentAssetRowId
                null, null, null,                // marketCode · symbol · quantity
                List.of(), null, null, null);    // holdings · createAt · modifyAt · monthlyUsedAmount
    }

    // === 잔액 상한·부호 (QA #17 #19) ===

    @Test
    @DisplayName("POST /asset — 잔액 99조는 400 (종전엔 그대로 저장)")
    void createRejectsOversizedBalance() throws Exception {
        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"통장\",\"assetType\":\"BANK_ACCOUNT\",\"balance\":99999999999999}"))
                .andExpect(status().isBadRequest());

        verify(assetService, never()).createAsset(any());
    }

    @Test
    @DisplayName("POST /asset — 잔액 1,000억(경계)은 통과")
    void createAcceptsBalanceAtLimit() throws Exception {
        given(assetService.createAsset(any())).willReturn(sampleAsset());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"통장\",\"assetType\":\"BANK_ACCOUNT\",\"balance\":100000000000}"))
                .andExpect(status().isOk());

        verify(assetService).createAsset(any());
    }

    @Test
    @DisplayName("POST /asset — 음수 잔액도 크기만 본다(마이너스 통장·대출을 400 으로 막으면 안 된다)")
    void createAcceptsNegativeBalanceWithinLimit() throws Exception {
        given(assetService.createAsset(any())).willReturn(sampleAsset());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"마통\",\"assetType\":\"BANK_ACCOUNT\",\"balance\":-50000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"마통\",\"assetType\":\"BANK_ACCOUNT\",\"balance\":-100000000001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /asset — isOverdraft 를 그대로 서비스 커맨드에 실어 보낸다")
    void createPassesOverdraftFlag() throws Exception {
        given(assetService.createAsset(any())).willReturn(sampleAsset());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"마통\",\"assetType\":\"BANK_ACCOUNT\","
                                + "\"balance\":50000,\"isOverdraft\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AssetServiceDto.CreateAssetCommand> c =
                ArgumentCaptor.forClass(AssetServiceDto.CreateAssetCommand.class);
        verify(assetService).createAsset(c.capture());
        assertThat(c.getValue().isOverdraft()).isTrue();
        // 부호는 서비스가 씌운다 — 컨트롤러는 사용자가 친 절대값을 그대로 넘긴다.
        assertThat(c.getValue().balance()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("POST /asset — isOverdraft 를 안 보내면 null 로 넘어간다(옛 클라이언트 폴백)")
    void createLeavesOverdraftNullForLegacyClient() throws Exception {
        given(assetService.createAsset(any())).willReturn(sampleAsset());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"통장\",\"assetType\":\"BANK_ACCOUNT\",\"balance\":-50000}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AssetServiceDto.CreateAssetCommand> c =
                ArgumentCaptor.forClass(AssetServiceDto.CreateAssetCommand.class);
        verify(assetService).createAsset(c.capture());
        assertThat(c.getValue().isOverdraft()).isNull();
    }

    @Test
    @DisplayName("PUT /asset/{id} — 잔액 99조는 400")
    void updateRejectsOversizedBalance() throws Exception {
        mockMvc.perform(put("/api/v1/asset/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"balance\":99999999999999}"))
                .andExpect(status().isBadRequest());

        verify(assetService, never()).updateAsset(any(Long.class), any(Long.class), any());
    }

    @Test
    @DisplayName("POST /asset — 한도 1,000억 초과는 400, 음수 한도도 400")
    void creditLimitBounded() throws Exception {
        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"카드\",\"assetType\":\"CREDIT_CARD\",\"creditLimit\":100000000001}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/asset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetName\":\"카드\",\"assetType\":\"CREDIT_CARD\",\"creditLimit\":-1}"))
                .andExpect(status().isBadRequest());

        verify(assetService, never()).createAsset(any());
    }
}
