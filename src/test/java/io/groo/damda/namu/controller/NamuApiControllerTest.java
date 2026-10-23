package io.groo.damda.namu.controller;

import io.groo.core.util.MessageResolver;
import io.groo.damda.common.config.web.WebConfig;
import io.groo.damda.namu.dto.NamuQuoteDto;
import io.groo.damda.namu.service.NamuAccountDetailQueryService;
import io.groo.damda.namu.service.NamuMarketQueryService;
import io.groo.damda.namu.service.NamuQueryService;
import io.groo.damda.security.filter.JwtAuthenticationFilter;
import io.groo.damda.security.resolver.LoginUserArgumentResolver;
import io.groo.damda.securities.service.dto.CandlePage;
import io.groo.damda.securities.service.dto.CandleQuery;
import io.groo.damda.securities.service.dto.InstrumentRef;
import io.groo.damda.securities.type.CandleInterval;
import io.groo.damda.support.security.WithLoginUser;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 13b-0 — 나무 새 경로 열넷의 매핑. 서비스는 목으로 격리하고, 인증 사용자(rowId) 위임 ·
 * 파라미터 매핑 · 상한 · 모양만 본다.
 */
@WebMvcTest(controllers = NamuApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class NamuApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NamuQueryService namuQueryService;
    @MockitoBean private NamuMarketQueryService namuMarketQueryService;
    @MockitoBean private NamuAccountDetailQueryService namuAccountDetailQueryService;
    // groo-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /namu/kr/quote · /namu/gb/quote — 상세 시세")
    void quotes() throws Exception {
        mockMvc.perform(get("/api/v1/namu/kr/quote").param("symbol", "005930")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/gb/quote").param("symbol", "MOA")).andExpect(status().isOk());

        verify(namuMarketQueryService).getKrQuote(1L, "005930");
        verify(namuMarketQueryService).getGbQuote(1L, "MOA");
    }

    @Test
    @DisplayName("GET /namu/{kr,gb}/executions — 기본 20건")
    void executions() throws Exception {
        mockMvc.perform(get("/api/v1/namu/kr/executions").param("symbol", "005930")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/gb/executions").param("symbol", "MOA").param("count", "30"))
                .andExpect(status().isOk());

        verify(namuMarketQueryService).getKrExecutions(1L, "005930", 20);
        verify(namuMarketQueryService).getGbExecutions(1L, "MOA", 30);
    }

    @Test
    @DisplayName("GET /namu/kr/investors · /namu/kr/etf/components · /namu/kr/after-hours · /namu/margin")
    void others() throws Exception {
        mockMvc.perform(get("/api/v1/namu/kr/investors").param("symbol", "005930")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/kr/etf/components").param("symbol", "069500")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/kr/after-hours").param("symbol", "005930")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/margin")).andExpect(status().isOk());

        verify(namuMarketQueryService).getKrInvestors(1L, "005930", 5);
        verify(namuMarketQueryService).getEtfComponents(1L, "069500");
        verify(namuMarketQueryService).getKrAfterHours(1L, "005930");
        verify(namuMarketQueryService).getMargin(1L);
    }

    @Test
    @DisplayName("GET /namu/prices — 중복·공백을 걷고 50개에서 자른다 (/securities/prices 와 같은 상한)")
    @SuppressWarnings("unchecked")
    void pricesCapsAtFifty() throws Exception {
        String symbols = IntStream.rangeClosed(1, 60).mapToObj(i -> "S" + i).collect(Collectors.joining(", "))
                + ",S1,,";
        given(namuQueryService.getListQuotes(anyLong(), any())).willReturn(List.of(
                new NamuQuoteDto.ListQuote("S1", new BigDecimal("100"), "KRW", new BigDecimal("99"), "REGULAR")));

        mockMvc.perform(get("/api/v1/namu/prices").param("symbols", symbols))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("S1"))
                .andExpect(jsonPath("$.data[0].marketState").value("REGULAR"));

        ArgumentCaptor<List<InstrumentRef>> captor = ArgumentCaptor.forClass(List.class);
        verify(namuQueryService).getListQuotes(eq(1L), captor.capture());
        assertThat(captor.getValue()).hasSize(50);
        assertThat(captor.getValue().get(0)).isEqualTo(InstrumentRef.of("S1"));
        assertThat(captor.getValue().get(49)).isEqualTo(InstrumentRef.of("S50"));
    }

    @Test
    @DisplayName("GET /namu/prices — 종목이 비면 빈 목록 · 상류를 부르지 않는다")
    void pricesEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/namu/prices").param("symbols", " , "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(namuQueryService, never()).getListQuotes(anyLong(), any());
    }

    @Test
    @DisplayName("GET /namu/candles — 주봉 1w 를 받는다 (나무 제공자를 바로 부른다)")
    void candlesWeekly() throws Exception {
        given(namuQueryService.getCandles(anyLong(), any())).willReturn(new CandlePage(List.of(), null));

        mockMvc.perform(get("/api/v1/namu/candles")
                        .param("symbol", " 005930 ").param("interval", "1w").param("size", "1000"))
                .andExpect(status().isOk());

        ArgumentCaptor<CandleQuery> captor = ArgumentCaptor.forClass(CandleQuery.class);
        verify(namuQueryService).getCandles(eq(1L), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CandleQuery("005930", CandleInterval.WEEK_1, 200, null, null));
    }

    @Test
    @DisplayName("GET /namu/candles — 모르는 주기 · 빈 종목은 400")
    void candlesRejects() throws Exception {
        mockMvc.perform(get("/api/v1/namu/candles").param("symbol", "005930").param("interval", "5m"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/namu/candles").param("symbol", " ").param("interval", "1d"))
                .andExpect(status().isBadRequest());

        verify(namuQueryService, never()).getCandles(anyLong(), any());
    }

    @Test
    @DisplayName("GET /namu/exchange-rate — {base, quote, rate} 는 /securities/exchange-rate 와 같은 모양")
    void exchangeRate() throws Exception {
        given(namuQueryService.getFxRate(1L, "USD")).willReturn(new BigDecimal("1383.5"));

        mockMvc.perform(get("/api/v1/namu/exchange-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.quote").value("KRW"))
                .andExpect(jsonPath("$.data.rate").value(1383.5));
    }

    @Test
    @DisplayName("GET /namu/account/assets · realized-pnl · rights — 계좌 자세히")
    void accountDetail() throws Exception {
        mockMvc.perform(get("/api/v1/namu/account/assets")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/account/realized-pnl").param("from", "2026-08-01").param("to", "2026-10-23"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/account/rights")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/namu/account/rights").param("kind", "held").param("from", "2026-01-01"))
                .andExpect(status().isOk());

        verify(namuAccountDetailQueryService).getAssetStatus(1L);
        verify(namuAccountDetailQueryService).getRealizedPnl(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 23));
        verify(namuAccountDetailQueryService).getRights(1L, "scheduled", null);
        verify(namuAccountDetailQueryService).getRights(1L, "held", LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("GET /namu/account/realized-pnl — 날짜 형식이 틀리면 4xx")
    void realizedPnlBadDate() throws Exception {
        mockMvc.perform(get("/api/v1/namu/account/realized-pnl").param("from", "20260801").param("to", "2026-10-23"))
                .andExpect(status().is4xxClientError());
    }
}
