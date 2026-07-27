package com.porest.desk.card.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.card.service.CardPaymentService;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardBilling API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 경로 매핑·로그인 사용자 위임·응답 변환을 검증한다.
 */
@WebMvcTest(controllers = CardBillingApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CardBillingApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CardPaymentService cardPaymentService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private CardPaymentServiceDto.BillingInfo sampleBilling() {
        return new CardPaymentServiceDto.BillingInfo(
                1L, 50L, 200L, 12000L,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1),
                BillingStatus.COMPLETED, 300L, null, LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    @DisplayName("GET /asset/{id}/billing — id·로그인 사용자로 청구 조회 위임")
    void getCardBilling() throws Exception {
        CardPaymentServiceDto.CardBillingInfo info = new CardPaymentServiceDto.CardBillingInfo(
                50L, 12000L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 1), 15, 200L, List.of(sampleBilling()));
        given(cardPaymentService.getCardBilling(50L, 1L)).willReturn(info);

        mockMvc.perform(get("/api/v1/asset/{id}/billing", 50L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardAssetRowId").value(50))
                .andExpect(jsonPath("$.data.upcomingAmount").value(12000))
                .andExpect(jsonPath("$.data.upcomingPeriodStart").value("2026-07-01"))
                .andExpect(jsonPath("$.data.upcomingPeriodEnd").value("2026-07-31"))
                .andExpect(jsonPath("$.data.paymentDay").value(15))
                .andExpect(jsonPath("$.data.paymentAssetRowId").value(200))
                .andExpect(jsonPath("$.data.history[0].rowId").value(1))
                .andExpect(jsonPath("$.data.history[0].status").value("COMPLETED"));

        verify(cardPaymentService).getCardBilling(eq(50L), eq(1L));
    }

    @Test
    @DisplayName("POST /asset/{id}/pay — id·로그인 사용자로 수동 결제 위임")
    void payCard() throws Exception {
        given(cardPaymentService.payCard(50L, 1L)).willReturn(sampleBilling());

        mockMvc.perform(post("/api/v1/asset/{id}/pay", 50L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(1))
                .andExpect(jsonPath("$.data.cardAssetRowId").value(50))
                .andExpect(jsonPath("$.data.billingAmount").value(12000))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(cardPaymentService).payCard(eq(50L), eq(1L));
    }

    @Test
    @DisplayName("GET /asset/{id}/billing — 잘못된 타입 경로변수 → 400")
    void getCardBilling_typeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/asset/{id}/billing", "not-a-number"))
                .andExpect(status().isBadRequest());
    }
}
