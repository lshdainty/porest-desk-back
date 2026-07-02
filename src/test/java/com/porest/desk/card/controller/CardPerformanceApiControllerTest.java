package com.porest.desk.card.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.card.service.CardPerformanceService;
import com.porest.desk.card.service.dto.CardPerformanceServiceDto;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardPerformance API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 쿼리 파라미터→PerformanceQuery 매핑(YearMonth 파싱 포함)을 검증한다.
 */
@WebMvcTest(controllers = CardPerformanceApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CardPerformanceApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CardPerformanceService cardPerformanceService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /card-performance — 로그인 사용자·쿼리로 PerformanceQuery 매핑 및 응답 반환")
    void getCardPerformance() throws Exception {
        CardPerformanceServiceDto.PerformanceInfo info = new CardPerformanceServiceDto.PerformanceInfo(
                50L, YearMonth.of(2026, 7), 300000, "30만원", true,
                150000L, 0.5, false, 150000L);
        given(cardPerformanceService.getPerformance(any())).willReturn(info);

        mockMvc.perform(get("/api/v1/card-performance")
                        .param("assetRowId", "50")
                        .param("yearMonth", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetRowId").value(50))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.requiredAmount").value(300000))
                .andExpect(jsonPath("$.data.currentAmount").value(150000))
                .andExpect(jsonPath("$.data.isAchieved").value(false));

        var captor = ArgumentCaptor.forClass(CardPerformanceServiceDto.PerformanceQuery.class);
        verify(cardPerformanceService).getPerformance(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().assetRowId()).isEqualTo(50L);
        assertThat(captor.getValue().yearMonth()).isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    @DisplayName("GET /card-performance — 잘못된 타입 assetRowId → 400")
    void getCardPerformance_typeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/card-performance")
                        .param("assetRowId", "not-a-number")
                        .param("yearMonth", "2026-07"))
                .andExpect(status().isBadRequest());
    }
}
