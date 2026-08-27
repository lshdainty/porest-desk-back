package com.porest.desk.securities.controller;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.core.exception.ExternalServiceException;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.securities.service.SecuritiesCandleProvider;
import com.porest.desk.securities.service.SecuritiesCandleProviders;
import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.SecuritiesPriceProviders;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.service.dto.SecuritiesCandle;
import com.porest.desk.securities.type.CandleInterval;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 증권사 무관 시세 API.
 *
 * <p>이게 없던 동안 앱·웹이 {@code /api/v1/toss/**} 를 직접 불러, <b>나무만 연결한 사용자는
 * 자산 평가액이 0/누락으로 보였다.</b> 여기서는 <b>기본 소스가 무엇이든 같은 모양으로
 * 응답하는지</b>와 <b>클라이언트가 증권사를 몰라도 되는지</b>를 본다.
 */
@WebMvcTest(controllers = SecuritiesApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class SecuritiesApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SecuritiesPriceProviders priceProviders;
    @MockitoBean private SecuritiesPriceProvider provider;
    @MockitoBean private SecuritiesCandleProviders candleProviders;
    @MockitoBean private SecuritiesCandleProvider candleProvider;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @BeforeEach
    void setUp() {
        given(priceProviders.forUser(1L)).willReturn(provider);
        given(candleProviders.forUser(1L)).willReturn(candleProvider);
    }

    @Test
    @DisplayName("GET /prices — 콤마 구분 심볼을 종목 목록으로 넘긴다. 증권사는 서버가 고른다")
    void getPrices() throws Exception {
        given(provider.getPrices(anyLong(), any())).willReturn(List.of(
                new PriceQuote("005930", new BigDecimal("70000"), "KRW", new BigDecimal("69500")),
                PriceQuote.of("AAPL", new BigDecimal("185.70"), "USD")));

        mockMvc.perform(get("/api/v1/securities/prices").param("symbols", "005930,AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("005930"))
                .andExpect(jsonPath("$.data[0].price").value(70000))
                .andExpect(jsonPath("$.data[0].previousClose").value(69500))
                // 전일 종가를 못 주는 증권사도 있다 — 그 자리는 비어 온다.
                .andExpect(jsonPath("$.data[1].previousClose").doesNotExist());

        var captor = forClass(List.class);
        verify(provider).getPrices(anyLong(), captor.capture());
        assertThat(captor.getValue())
            .containsExactly(InstrumentRef.of("005930"), InstrumentRef.of("AAPL"));
    }

    @Test
    @DisplayName("GET /prices — 공백·빈 항목·중복을 걸러낸다")
    void getPricesNormalizesInput() throws Exception {
        given(provider.getPrices(anyLong(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/prices").param("symbols", " 005930 ,,005930, AAPL "))
                .andExpect(status().isOk());

        var captor = forClass(List.class);
        verify(provider).getPrices(anyLong(), captor.capture());
        assertThat(captor.getValue())
            .containsExactly(InstrumentRef.of("005930"), InstrumentRef.of("AAPL"));
    }

    @Test
    @DisplayName("GET /prices — 심볼이 하나도 없으면 증권사를 부르지 않는다")
    void getPricesSkipsWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/securities/prices").param("symbols", " , "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(provider, org.mockito.Mockito.never()).getPrices(anyLong(), any());
    }

    @Test
    @DisplayName("GET /prices — 상한(50)을 넘겨도 그만큼만 부른다. 나무는 종목마다 1콜이라 유량이 걸린다")
    void getPricesCapsBatchSize() throws Exception {
        given(provider.getPrices(anyLong(), any())).willReturn(List.of());
        String many = java.util.stream.IntStream.range(0, 80)
            .mapToObj(i -> "SYM" + i).collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(get("/api/v1/securities/prices").param("symbols", many))
                .andExpect(status().isOk());

        var captor = forClass(List.class);
        verify(provider).getPrices(anyLong(), captor.capture());
        assertThat(captor.getValue()).hasSize(50);
    }

    @Test
    @DisplayName("GET /exchange-rate — 기본은 USD→KRW")
    void getExchangeRate() throws Exception {
        given(provider.getFxRate(1L, "USD", "KRW")).willReturn(new BigDecimal("1383.50"));

        mockMvc.perform(get("/api/v1/securities/exchange-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("USD"))
                .andExpect(jsonPath("$.data.quote").value("KRW"))
                .andExpect(jsonPath("$.data.rate").value(1383.50));
    }

    @Test
    @DisplayName("GET /exchange-rate — 못 구하면 rate 가 null 이다. 호출부가 외화 환산을 접는다")
    void getExchangeRateNullable() throws Exception {
        given(provider.getFxRate(1L, "USD", "KRW")).willReturn(null);

        mockMvc.perform(get("/api/v1/securities/exchange-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").doesNotExist());
    }

    // === 캔들 ===
    //
    // 이게 없던 동안 캔들 경로가 /api/v1/toss/candles 하나뿐이라 **나무만 연결한 사용자는
    // 차트를 아예 못 봤다.** 여기서 지키는 것은 두 가지다.
    //  ① 응답 모양이 /api/v1/toss/candles 와 같다 — 프론트가 URL 만 바꾸면 되게
    //  ② 상한·주기·커서를 서버가 정규화한다 — 증권사마다 달라지면 화면 커서 루프가 헛돈다

    private static CandlePage onePage(String nextCursor) {
        return new CandlePage(List.of(new SecuritiesCandle(
                "2026-08-26T00:00:00+09:00", "69000", "70500", "68800", "70000", "12345678", "KRW")),
                nextCursor);
    }

    @Test
    @DisplayName("GET /candles — 응답이 CursorResponse 다. 토스 경로와 같은 모양이라 프론트는 URL 만 바꾼다")
    void getCandles() throws Exception {
        given(candleProvider.getCandles(anyLong(), any())).willReturn(onePage("20260824"));

        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "1d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].timestamp").value("2026-08-26T00:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[0].openPrice").value("69000"))
                .andExpect(jsonPath("$.data.content[0].closePrice").value("70000"))
                .andExpect(jsonPath("$.data.content[0].volume").value("12345678"))
                .andExpect(jsonPath("$.data.meta.hasNext").value(true))
                .andExpect(jsonPath("$.data.meta.nextCursor").value("20260824"));
    }

    @Test
    @DisplayName("GET /candles — 커서가 없으면 여기가 끝이다")
    void getCandlesLastPage() throws Exception {
        given(candleProvider.getCandles(anyLong(), any())).willReturn(onePage(null));

        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "1d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.hasNext").value(false))
                .andExpect(jsonPath("$.data.meta.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("GET /candles — size 미지정은 200, 초과는 200 으로 자른다(토스 상한과 같은 값)")
    void getCandlesCapsSize() throws Exception {
        given(candleProvider.getCandles(anyLong(), any())).willReturn(onePage(null));

        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "1d").param("size", "1000"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "1d"))
                .andExpect(status().isOk());

        var captor = forClass(CandleQuery.class);
        verify(candleProvider, org.mockito.Mockito.times(2)).getCandles(anyLong(), captor.capture());
        assertThat(captor.getAllValues()).extracting(CandleQuery::size).containsExactly(200, 200);
    }

    @Test
    @DisplayName("GET /candles — 주기·커서·수정주가를 그대로 넘긴다. 커서 뜻은 증권사가 정한다")
    void getCandlesPassesQuery() throws Exception {
        given(candleProvider.getCandles(anyLong(), any())).willReturn(onePage(null));

        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", " 005930 ").param("interval", "1m")
                        .param("size", "50").param("cursor", "20260824").param("adjusted", "true"))
                .andExpect(status().isOk());

        var captor = forClass(CandleQuery.class);
        verify(candleProvider).getCandles(anyLong(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new CandleQuery("005930", CandleInterval.MINUTE_1, 50, "20260824", true));
    }

    @Test
    @DisplayName("GET /candles — 모르는 주기는 400. 조용히 일봉으로 떨어뜨리면 차트가 멈춘 것처럼 보인다")
    void getCandlesRejectsUnknownInterval() throws Exception {
        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "5m"))
                .andExpect(status().isBadRequest());

        verify(candleProvider, org.mockito.Mockito.never()).getCandles(anyLong(), any());
    }

    @Test
    @DisplayName("GET /candles — 종목이 비면 400")
    void getCandlesRejectsBlankSymbol() throws Exception {
        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "  ").param("interval", "1d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /candles — 연결이 없으면 403. 캔들 미지원(409)과 뜻이 다르다")
    void getCandlesRequiresConnection() throws Exception {
        given(candleProviders.forUser(1L))
                .willThrow(new ExternalServiceException(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED));

        mockMvc.perform(get("/api/v1/securities/candles")
                        .param("symbol", "005930").param("interval", "1d"))
                .andExpect(status().isForbidden());
    }
}
