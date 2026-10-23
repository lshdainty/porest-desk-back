package io.groo.damda.toss.service;

import io.groo.core.controller.dto.CursorResponse;
import io.groo.core.exception.InvalidValueException;
import io.groo.damda.securities.service.dto.InvestorTradingDay;
import io.groo.damda.toss.client.TossApiClient;
import io.groo.damda.toss.dto.TossAccountDto;
import io.groo.damda.toss.dto.TossIndicatorDto;
import io.groo.damda.toss.dto.TossMarketDto;
import io.groo.damda.toss.dto.TossRankingDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 캔들 커서 프록시 매핑 — 토스 CandlePageResponse(candles/nextBefore) →
 * groo-core CursorResponse(content/meta.nextCursor), size cap(≤200), cursor→before 전달 검증.
 */
@ExtendWith(MockitoExtension.class)
class TossQueryServiceImplTest {

    @Mock private TossApiClient client;
    @Mock private io.groo.damda.stock.service.StockMasterResolver stockMasterResolver;
    @InjectMocks private TossQueryServiceImpl sut;

    private static final long USER = 1L;

    private static TossMarketDto.Candle candle(String ts) {
        return new TossMarketDto.Candle(ts, "100", "110", "90", "105", "1000", "KRW");
    }

    @Test
    @DisplayName("토스 candles/nextBefore 를 CursorResponse content/nextCursor 로 매핑(nextBefore 있으면 hasNext)")
    void getCandles_mapsToCursorResponse_withNextCursor() {
        TossMarketDto.CandlePageResponse page =
                new TossMarketDto.CandlePageResponse(List.of(candle("t1"), candle("t2")), "c123");
        given(client.<TossMarketDto.CandlePageResponse>get(eq(USER), eq("/api/v1/candles"), any(), any()))
                .willReturn(page);

        CursorResponse<TossMarketDto.Candle> result =
                sut.getCandles(USER, "005930", "1d", 200, null, null);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getMeta().isHasNext()).isTrue();
        assertThat(result.getMeta().getNextCursor()).isEqualTo("c123");
        assertThat(result.getMeta().getSize()).isEqualTo(200);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isEqualTo("200");
        assertThat(q.getValue().getFirst("before")).isNull();
    }

    @Test
    @DisplayName("nextBefore 가 null 이면 마지막 페이지 — hasNext=false, nextCursor=null")
    void getCandles_lastPage_noNextCursor() {
        TossMarketDto.CandlePageResponse page =
                new TossMarketDto.CandlePageResponse(List.of(candle("t1")), null);
        given(client.<TossMarketDto.CandlePageResponse>get(eq(USER), eq("/api/v1/candles"), any(), any()))
                .willReturn(page);

        CursorResponse<TossMarketDto.Candle> result =
                sut.getCandles(USER, "005930", "1d", 100, null, null);

        assertThat(result.getMeta().isHasNext()).isFalse();
        assertThat(result.getMeta().getNextCursor()).isNull();
    }

    @Test
    @DisplayName("size 200 초과/미지정은 토스 상한 200 으로 cap")
    void getCandles_capsSizeTo200() {
        sut.getCandles(USER, "005930", "1m", 390, null, null);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isEqualTo("200");
    }

    @Test
    @DisplayName("cursor 는 토스 before 파라미터로 전달(시간 역방향 다음 페이지)")
    void getCandles_passesCursorAsBefore() {
        sut.getCandles(USER, "005930", "1d", 200, "c999", null);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("before")).isEqualTo("c999");
    }

    @Test
    @DisplayName("랭킹 조회 — type/marketCountry/duration 필수, 선택 파라미터는 있을 때만 전달")
    void getRankings_buildsQuery() {
        TossRankingDto.RankingResponse resp = new TossRankingDto.RankingResponse("2026-07-31T09:00:00+09:00",
                List.of(new TossRankingDto.RankingItem(1, "005930", "KRW",
                        new TossRankingDto.RankingPrice("72000", "71000", "0.0141"), "1000", "72000000")));
        given(client.<TossRankingDto.RankingResponse>get(eq(USER), eq("/api/v1/rankings"), any(), any()))
                .willReturn(resp);

        TossRankingDto.RankingResponse result =
                sut.getRankings(USER, "TOP_GAINERS", "KR", "1d", true, 20);

        assertThat(result.rankings()).hasSize(1);
        assertThat(result.rankings().get(0).price().changeRate()).isEqualTo("0.0141");
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/rankings"), q.capture(), any());
        assertThat(q.getValue().getFirst("type")).isEqualTo("TOP_GAINERS");
        assertThat(q.getValue().getFirst("marketCountry")).isEqualTo("KR");
        assertThat(q.getValue().getFirst("duration")).isEqualTo("1d");
        assertThat(q.getValue().getFirst("excludeInvestmentCaution")).isEqualTo("true");
        assertThat(q.getValue().getFirst("count")).isEqualTo("20");
    }

    @Test
    @DisplayName("랭킹 조회 — 선택 파라미터 미지정 시 쿼리에서 생략")
    void getRankings_omitsOptionalParams() {
        sut.getRankings(USER, "MARKET_TRADING_AMOUNT", "US", "realtime", null, null);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/rankings"), q.capture(), any());
        assertThat(q.getValue().containsKey("excludeInvestmentCaution")).isFalse();
        assertThat(q.getValue().containsKey("count")).isFalse();
    }

    @Test
    @DisplayName("시장 지표 현재가 — symbols 콤마 다건을 그대로 전달")
    void getMarketIndicatorPrices_passesSymbols() {
        given(client.<List<TossIndicatorDto.IndicatorPriceResponse>>get(
                eq(USER), eq("/api/v1/market-indicators/prices"), any(), any()))
                .willReturn(List.of(new TossIndicatorDto.IndicatorPriceResponse("KOSPI", null, "2812.45")));

        List<TossIndicatorDto.IndicatorPriceResponse> result =
                sut.getMarketIndicatorPrices(USER, "KOSPI,KOSDAQ");

        assertThat(result).singleElement()
                .extracting(TossIndicatorDto.IndicatorPriceResponse::lastPrice).isEqualTo("2812.45");
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/market-indicators/prices"), q.capture(), any());
        assertThat(q.getValue().getFirst("symbols")).isEqualTo("KOSPI,KOSDAQ");
    }

    @Test
    @DisplayName("시장 지표 캔들 — 심볼 path 변수 + candles 와 동일한 커서 정규화")
    void getMarketIndicatorCandles_mapsToCursorResponse() {
        TossIndicatorDto.IndicatorCandlePageResponse page = new TossIndicatorDto.IndicatorCandlePageResponse(
                List.of(new TossIndicatorDto.IndicatorCandle("t1", "2800", "2820", "2790", "2812", "0")), "b777");
        given(client.<TossIndicatorDto.IndicatorCandlePageResponse>getPath(
                eq(USER), eq("/api/v1/market-indicators/{symbol}/candles"), eq(java.util.Map.of("symbol", "KOSPI")), any(MultiValueMap.class), any()))
                .willReturn(page);

        CursorResponse<TossIndicatorDto.IndicatorCandle> result =
                sut.getMarketIndicatorCandles(USER, "KOSPI", "1d", 30, "b111");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getMeta().isHasNext()).isTrue();
        assertThat(result.getMeta().getNextCursor()).isEqualTo("b777");
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).getPath(eq(USER), eq("/api/v1/market-indicators/{symbol}/candles"),
                eq(java.util.Map.of("symbol", "KOSPI")), q.capture(), any());
        assertThat(q.getValue().getFirst("interval")).isEqualTo("1d");
        assertThat(q.getValue().getFirst("count")).isEqualTo("30");
        assertThat(q.getValue().getFirst("before")).isEqualTo("b111");
    }

    // === 13b-0 ===

    @Test
    @DisplayName("[13b-0 줄 7] 주봉 1w 는 토스가 거절한다 — 상류에 보내지 않는다 (나무 전용)")
    void getCandles_rejectsWeekly() {
        assertThatThrownBy(() -> sut.getCandles(USER, "005930", "1w", 100, null, null))
                .isInstanceOf(InvalidValueException.class);

        verify(client, never()).get(any(), any(), any(), any());
    }

    @Test
    @DisplayName("[13b-0 줄 5] 투자자별 — 나무와 같은 InvestorTradingDay · 당일 잠정의 개인 null 은 그대로 null · count 상한 100")
    void getInvestorTrading_mapsToCommonShape() {
        TossMarketDto.InvestorTradingResponse res = new TossMarketDto.InvestorTradingResponse(null, List.of(
                new TossMarketDto.InvestorTradingRecord("2026-10-23", null,
                        new TossMarketDto.InvestorSide("-300"), new TossMarketDto.InvestorSide("100")),
                new TossMarketDto.InvestorTradingRecord("2026-10-22", new TossMarketDto.InvestorSide("200"),
                        new TossMarketDto.InvestorSide("-50"), null)));
        given(client.<TossMarketDto.InvestorTradingResponse>getPath(eq(USER),
                eq("/api/v1/stocks/{symbol}/investor-trading"), eq(java.util.Map.of("symbol", "005930")), any(), any()))
                .willReturn(res);

        List<InvestorTradingDay> days = sut.getInvestorTrading(USER, "005930", 500);

        assertThat(days).containsExactly(
                new InvestorTradingDay("2026-10-23", null, "-300", "100"),
                new InvestorTradingDay("2026-10-22", "200", "-50", null));
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).getPath(eq(USER), eq("/api/v1/stocks/{symbol}/investor-trading"), any(), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isEqualTo("100");
    }

    @Test
    @DisplayName("[13b-0 줄 5] 투자자별 — 응답이 비면 빈 목록 · count 를 안 주면 쿼리에 싣지 않는다")
    void getInvestorTrading_empty() {
        given(client.<TossMarketDto.InvestorTradingResponse>getPath(eq(USER), any(), any(), any(), any()))
                .willReturn(null);

        assertThat(sut.getInvestorTrading(USER, "005930", null)).isEmpty();
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).getPath(eq(USER), any(), any(), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isNull();
    }

    @Test
    @DisplayName("[13b-0 줄 5] 투자자별 — 미국 종목은 400 이 아니라 빈 목록, 상류를 부르지 않는다")
    void getInvestorTrading_usSymbolReturnsEmptyWithoutUpstream() {
        io.groo.damda.stock.domain.StockMaster us = io.groo.damda.stock.domain.StockMaster.create(
                io.groo.damda.stock.type.MasterSource.KIS,
                io.groo.damda.stock.client.dto.InstrumentRecord.kis(io.groo.damda.stock.type.StockMarket.NAS,
                        "MOA", null, null, "MOA", "MOA", io.groo.damda.stock.type.StockSecurityType.STOCK, "USD"));
        given(stockMasterResolver.resolve((io.groo.damda.stock.type.StockMarket) null, "MOA"))
                .willReturn(java.util.Optional.of(us));

        assertThat(sut.getInvestorTrading(USER, "MOA", 5)).isEmpty();

        verify(client, never()).getPath(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[13b-0 검수] 주문 가능 — KRW · USD 가 아닌 통화는 400 (상류 502 로 새지 않는다)")
    void getBuyingPower_rejectsUnknownCurrency() {
        assertThatThrownBy(() -> sut.getBuyingPower(USER, 77L, "JPY")).isInstanceOf(InvalidValueException.class);

        verify(client, never()).getForUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[13b-0 줄 6] 주문 가능 — 본인 계좌 헤더(accountSeq)와 통화를 싣는다")
    void getBuyingPower() {
        given(client.<TossAccountDto.BuyingPower>getForUser(eq(USER), eq("/api/v1/buying-power"), any(), eq(77L), any()))
                .willReturn(new TossAccountDto.BuyingPower("USD", "1234.56"));

        TossAccountDto.BuyingPower bp = sut.getBuyingPower(USER, 77L, "USD");

        assertThat(bp.cashBuyingPower()).isEqualTo("1234.56");
        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).getForUser(eq(USER), eq("/api/v1/buying-power"), q.capture(), eq(77L), any());
        assertThat(q.getValue().getFirst("currency")).isEqualTo("USD");
    }

    @Test
    @DisplayName("[13b-0 줄 7] 수수료율 — 본인 계좌 헤더로 그대로 내린다")
    void getCommissions() {
        List<TossAccountDto.Commission> list = List.of(
                new TossAccountDto.Commission("KR", "0.00015", "2026-01-01", null),
                new TossAccountDto.Commission("US", "0.0025", null, null));
        given(client.<List<TossAccountDto.Commission>>getForUser(eq(USER), eq("/api/v1/commissions"), any(), eq(77L), any()))
                .willReturn(list);

        assertThat(sut.getCommissions(USER, 77L)).isEqualTo(list);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<MultiValueMap<String, String>> queryCaptor() {
        return ArgumentCaptor.forClass(MultiValueMap.class);
    }
}
