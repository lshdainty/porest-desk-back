package com.porest.desk.toss.service;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.toss.client.TossApiClient;
import com.porest.desk.toss.client.dto.TossEnvelope;
import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.dto.TossIndicatorDto;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.dto.TossMarketInfoDto;
import com.porest.desk.toss.dto.TossRankingDto;
import com.porest.desk.toss.dto.TossStockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

/**
 * {@link TossQueryService} 구현. 토스증권 엔드포인트별 경로/쿼리를 구성해
 * {@link TossApiClient} 로 위임하고, envelope 언래핑된 페이로드를 그대로 반환한다.
 */
@Service
@RequiredArgsConstructor
public class TossQueryServiceImpl implements TossQueryService {

    private final TossApiClient client;

    /** 토스 candles count 상한(min:1 max:200). 한 페이지 최대 크기. */
    private static final int TOSS_CANDLE_MAX = 200;

    // === Market Data ===

    @Override
    public TossMarketDto.OrderbookResponse getOrderbook(Long userRowId, String symbol) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        return client.get(userRowId, "/api/v1/orderbook", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.OrderbookResponse>>() {});
    }

    @Override
    public List<TossMarketDto.PriceResponse> getPrices(Long userRowId, String symbols) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbols", symbols);
        return client.get(userRowId, "/api/v1/prices", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossMarketDto.PriceResponse>>>() {});
    }

    @Override
    public List<TossMarketDto.Trade> getTrades(Long userRowId, String symbol, Integer count) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        if (count != null) {
            q.add("count", String.valueOf(count));
        }
        return client.get(userRowId, "/api/v1/trades", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossMarketDto.Trade>>>() {});
    }

    @Override
    public TossMarketDto.PriceLimitResponse getPriceLimits(Long userRowId, String symbol) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        return client.get(userRowId, "/api/v1/price-limits", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.PriceLimitResponse>>() {});
    }

    @Override
    public CursorResponse<TossMarketDto.Candle> getCandles(Long userRowId, String symbol, String interval, Integer size, String cursor, Boolean adjusted) {
        int pageSize = (size == null || size <= 0) ? TOSS_CANDLE_MAX : Math.min(size, TOSS_CANDLE_MAX);

        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        q.add("interval", interval);
        q.add("count", String.valueOf(pageSize));
        if (cursor != null && !cursor.isBlank()) {
            // 커서 = 직전 페이지의 nextBefore. 토스는 before 파라미터로 더 과거 페이지를 가리킨다.
            q.add("before", cursor);
        }
        if (adjusted != null) {
            q.add("adjusted", String.valueOf(adjusted));
        }

        TossMarketDto.CandlePageResponse page = client.get(userRowId, "/api/v1/candles", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.CandlePageResponse>>() {});

        List<TossMarketDto.Candle> candles = (page == null || page.candles() == null) ? List.of() : page.candles();
        String nextBefore = page == null ? null : page.nextBefore();
        boolean hasNext = nextBefore != null && !nextBefore.isBlank();
        // 토스 nextBefore -> CursorResponse.meta.nextCursor (forward-only, 시간 역방향). 수동 of 로 트리밍 없이 그대로 매핑.
        return CursorResponse.of(candles, pageSize, hasNext, nextBefore);
    }

    // === Stock Info ===

    @Override
    public List<TossStockDto.StockInfo> getStocks(Long userRowId, String symbols) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbols", symbols);
        return client.get(userRowId, "/api/v1/stocks", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossStockDto.StockInfo>>>() {});
    }

    @Override
    public List<TossStockDto.StockWarning> getStockWarnings(Long userRowId, String symbol) {
        return client.getPath(userRowId, "/api/v1/stocks/{symbol}/warnings", Map.of("symbol", symbol),
                new ParameterizedTypeReference<TossEnvelope<List<TossStockDto.StockWarning>>>() {});
    }

    // === Rankings ===

    @Override
    public TossRankingDto.RankingResponse getRankings(Long userRowId, String type, String marketCountry,
                                                      String duration, Boolean excludeInvestmentCaution, Integer count) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("type", type);
        q.add("marketCountry", marketCountry);
        q.add("duration", duration);
        if (excludeInvestmentCaution != null) {
            q.add("excludeInvestmentCaution", String.valueOf(excludeInvestmentCaution));
        }
        if (count != null) {
            q.add("count", String.valueOf(count));
        }
        return client.get(userRowId, "/api/v1/rankings", q,
                new ParameterizedTypeReference<TossEnvelope<TossRankingDto.RankingResponse>>() {});
    }

    // === Market Indicators ===

    @Override
    public List<TossIndicatorDto.IndicatorPriceResponse> getMarketIndicatorPrices(Long userRowId, String symbols) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbols", symbols);
        return client.get(userRowId, "/api/v1/market-indicators/prices", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossIndicatorDto.IndicatorPriceResponse>>>() {});
    }

    @Override
    public CursorResponse<TossIndicatorDto.IndicatorCandle> getMarketIndicatorCandles(Long userRowId, String symbol,
                                                                                      String interval, Integer size, String cursor) {
        int pageSize = (size == null || size <= 0) ? TOSS_CANDLE_MAX : Math.min(size, TOSS_CANDLE_MAX);

        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("interval", interval);
        q.add("count", String.valueOf(pageSize));
        if (cursor != null && !cursor.isBlank()) {
            q.add("before", cursor);
        }

        TossIndicatorDto.IndicatorCandlePageResponse page = client.getPath(userRowId,
                "/api/v1/market-indicators/{symbol}/candles", Map.of("symbol", symbol), q,
                new ParameterizedTypeReference<TossEnvelope<TossIndicatorDto.IndicatorCandlePageResponse>>() {});

        List<TossIndicatorDto.IndicatorCandle> candles = (page == null || page.candles() == null) ? List.of() : page.candles();
        String nextBefore = page == null ? null : page.nextBefore();
        boolean hasNext = nextBefore != null && !nextBefore.isBlank();
        return CursorResponse.of(candles, pageSize, hasNext, nextBefore);
    }

    // === Market Info ===

    @Override
    public TossMarketInfoDto.ExchangeRateResponse getExchangeRate(Long userRowId, String baseCurrency, String quoteCurrency, String dateTime) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("baseCurrency", baseCurrency);
        q.add("quoteCurrency", quoteCurrency);
        if (dateTime != null) {
            q.add("dateTime", dateTime);
        }
        return client.get(userRowId, "/api/v1/exchange-rate", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketInfoDto.ExchangeRateResponse>>() {});
    }

    @Override
    public TossMarketInfoDto.KrMarketCalendarResponse getKrMarketCalendar(Long userRowId, String date) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (date != null) {
            q.add("date", date);
        }
        return client.get(userRowId, "/api/v1/market-calendar/KR", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketInfoDto.KrMarketCalendarResponse>>() {});
    }

    @Override
    public TossMarketInfoDto.UsMarketCalendarResponse getUsMarketCalendar(Long userRowId, String date) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (date != null) {
            q.add("date", date);
        }
        return client.get(userRowId, "/api/v1/market-calendar/US", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketInfoDto.UsMarketCalendarResponse>>() {});
    }

    // === Account / Asset ===

    @Override
    public List<TossAccountDto.Account> getAccounts(Long userRowId) {
        // 계좌목록은 키 주인 본인 계좌 → 사용자 개인 토큰
        return client.getForUser(userRowId, "/api/v1/accounts", null, null,
                new ParameterizedTypeReference<TossEnvelope<List<TossAccountDto.Account>>>() {});
    }

    @Override
    public TossAccountDto.HoldingsOverview getHoldings(Long userRowId, Long accountSeq, String symbol) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (symbol != null) {
            q.add("symbol", symbol);
        }
        // 보유주식은 본인 계좌 → 사용자 개인 토큰 + X-Tossinvest-Account
        return client.getForUser(userRowId, "/api/v1/holdings", q, accountSeq,
                new ParameterizedTypeReference<TossEnvelope<TossAccountDto.HoldingsOverview>>() {});
    }
}
