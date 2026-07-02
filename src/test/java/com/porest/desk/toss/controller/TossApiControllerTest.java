package com.porest.desk.toss.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.toss.service.TossQueryService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TossApiController(토스증권 조회 프록시) 슬라이스 테스트.
 *
 * <p>외부연동·읽기전용 프록시 — 서비스는 mock 으로 격리하고, 컨트롤러의 인증 사용자(rowId) 위임과
 * query/path 파라미터 매핑만 검증한다. FeatureGateInterceptor 는 슬라이스에서 미로드(ObjectProvider)라
 * 게이트 없이 통과한다.
 */
@WebMvcTest(controllers = TossApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class TossApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TossQueryService tossQueryService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /toss/orderbook — rowId·symbol 로 호가 조회")
    void getOrderbook() throws Exception {
        mockMvc.perform(get("/api/v1/toss/orderbook").param("symbol", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tossQueryService).getOrderbook(1L, "005930");
    }

    @Test
    @DisplayName("GET /toss/prices — rowId·symbols 로 현재가 목록 조회")
    void getPrices() throws Exception {
        given(tossQueryService.getPrices(1L, "005930,000660")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/prices").param("symbols", "005930,000660"))
                .andExpect(status().isOk());

        verify(tossQueryService).getPrices(1L, "005930,000660");
    }

    @Test
    @DisplayName("GET /toss/trades — count 지정 시 rowId·symbol·count 로 체결 조회")
    void getTrades_withCount() throws Exception {
        given(tossQueryService.getTrades(1L, "005930", 30)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/trades")
                        .param("symbol", "005930")
                        .param("count", "30"))
                .andExpect(status().isOk());

        verify(tossQueryService).getTrades(1L, "005930", 30);
    }

    @Test
    @DisplayName("GET /toss/trades — count 미지정이면 null 로 위임")
    void getTrades_withoutCount() throws Exception {
        given(tossQueryService.getTrades(eq(1L), eq("005930"), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/trades").param("symbol", "005930"))
                .andExpect(status().isOk());

        verify(tossQueryService).getTrades(eq(1L), eq("005930"), isNull());
    }

    @Test
    @DisplayName("GET /toss/price-limits — rowId·symbol 로 상/하한가 조회")
    void getPriceLimits() throws Exception {
        mockMvc.perform(get("/api/v1/toss/price-limits").param("symbol", "005930"))
                .andExpect(status().isOk());

        verify(tossQueryService).getPriceLimits(1L, "005930");
    }

    @Test
    @DisplayName("GET /toss/candles — 모든 query 파라미터가 서비스로 매핑")
    void getCandles() throws Exception {
        mockMvc.perform(get("/api/v1/toss/candles")
                        .param("symbol", "005930")
                        .param("interval", "1d")
                        .param("size", "100")
                        .param("cursor", "CUR123")
                        .param("adjusted", "true"))
                .andExpect(status().isOk());

        verify(tossQueryService).getCandles(1L, "005930", "1d", 100, "CUR123", true);
    }

    @Test
    @DisplayName("GET /toss/candles — 선택 파라미터 미지정이면 null 로 위임")
    void getCandles_optionalNull() throws Exception {
        mockMvc.perform(get("/api/v1/toss/candles")
                        .param("symbol", "005930")
                        .param("interval", "1m"))
                .andExpect(status().isOk());

        verify(tossQueryService).getCandles(eq(1L), eq("005930"), eq("1m"), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("GET /toss/stocks — rowId·symbols 로 종목 정보 조회")
    void getStocks() throws Exception {
        given(tossQueryService.getStocks(1L, "005930")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/stocks").param("symbols", "005930"))
                .andExpect(status().isOk());

        verify(tossQueryService).getStocks(1L, "005930");
    }

    @Test
    @DisplayName("GET /toss/stocks/{symbol}/warnings — path symbol 로 매수 유의사항 조회")
    void getStockWarnings() throws Exception {
        given(tossQueryService.getStockWarnings(1L, "005930")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/stocks/{symbol}/warnings", "005930"))
                .andExpect(status().isOk());

        verify(tossQueryService).getStockWarnings(1L, "005930");
    }

    @Test
    @DisplayName("GET /toss/exchange-rate — base/quote/dateTime 매핑")
    void getExchangeRate() throws Exception {
        mockMvc.perform(get("/api/v1/toss/exchange-rate")
                        .param("baseCurrency", "USD")
                        .param("quoteCurrency", "KRW")
                        .param("dateTime", "2026-07-01T00:00:00"))
                .andExpect(status().isOk());

        verify(tossQueryService).getExchangeRate(1L, "USD", "KRW", "2026-07-01T00:00:00");
    }

    @Test
    @DisplayName("GET /toss/market-calendar/KR — date 로 국내 장 일정 조회")
    void getKrMarketCalendar() throws Exception {
        mockMvc.perform(get("/api/v1/toss/market-calendar/KR").param("date", "2026-07-01"))
                .andExpect(status().isOk());

        verify(tossQueryService).getKrMarketCalendar(1L, "2026-07-01");
    }

    @Test
    @DisplayName("GET /toss/market-calendar/US — date 미지정이면 null 로 위임")
    void getUsMarketCalendar_withoutDate() throws Exception {
        mockMvc.perform(get("/api/v1/toss/market-calendar/US"))
                .andExpect(status().isOk());

        verify(tossQueryService).getUsMarketCalendar(eq(1L), isNull());
    }

    @Test
    @DisplayName("GET /toss/accounts — rowId 로 계좌 목록 조회")
    void getAccounts() throws Exception {
        given(tossQueryService.getAccounts(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/toss/accounts"))
                .andExpect(status().isOk());

        verify(tossQueryService).getAccounts(1L);
    }

    @Test
    @DisplayName("GET /toss/holdings — accountSeq·symbol 매핑")
    void getHoldings() throws Exception {
        mockMvc.perform(get("/api/v1/toss/holdings")
                        .param("accountSeq", "77")
                        .param("symbol", "005930"))
                .andExpect(status().isOk());

        verify(tossQueryService).getHoldings(1L, 77L, "005930");
    }
}
