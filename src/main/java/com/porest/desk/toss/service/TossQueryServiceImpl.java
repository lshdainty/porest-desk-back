package com.porest.desk.toss.service;

import com.porest.desk.toss.client.TossApiClient;
import com.porest.desk.toss.client.dto.TossEnvelope;
import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.dto.TossMarketInfoDto;
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

    // === Market Data ===

    @Override
    public TossMarketDto.OrderbookResponse getOrderbook(String symbol) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        return client.get("/api/v1/orderbook", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.OrderbookResponse>>() {});
    }

    @Override
    public List<TossMarketDto.PriceResponse> getPrices(String symbols) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbols", symbols);
        return client.get("/api/v1/prices", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossMarketDto.PriceResponse>>>() {});
    }

    @Override
    public List<TossMarketDto.Trade> getTrades(String symbol, Integer count) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        if (count != null) {
            q.add("count", String.valueOf(count));
        }
        return client.get("/api/v1/trades", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossMarketDto.Trade>>>() {});
    }

    @Override
    public TossMarketDto.PriceLimitResponse getPriceLimits(String symbol) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        return client.get("/api/v1/price-limits", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.PriceLimitResponse>>() {});
    }

    @Override
    public TossMarketDto.CandlePageResponse getCandles(String symbol, String interval, Integer count, String before, Boolean adjusted) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbol", symbol);
        q.add("interval", interval);
        if (count != null) {
            q.add("count", String.valueOf(count));
        }
        if (before != null) {
            q.add("before", before);
        }
        if (adjusted != null) {
            q.add("adjusted", String.valueOf(adjusted));
        }
        return client.get("/api/v1/candles", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketDto.CandlePageResponse>>() {});
    }

    // === Stock Info ===

    @Override
    public List<TossStockDto.StockInfo> getStocks(String symbols) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("symbols", symbols);
        return client.get("/api/v1/stocks", q,
                new ParameterizedTypeReference<TossEnvelope<List<TossStockDto.StockInfo>>>() {});
    }

    @Override
    public List<TossStockDto.StockWarning> getStockWarnings(String symbol) {
        return client.getPath("/api/v1/stocks/{symbol}/warnings", Map.of("symbol", symbol),
                new ParameterizedTypeReference<TossEnvelope<List<TossStockDto.StockWarning>>>() {});
    }

    // === Market Info ===

    @Override
    public TossMarketInfoDto.ExchangeRateResponse getExchangeRate(String baseCurrency, String quoteCurrency, String dateTime) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("baseCurrency", baseCurrency);
        q.add("quoteCurrency", quoteCurrency);
        if (dateTime != null) {
            q.add("dateTime", dateTime);
        }
        return client.get("/api/v1/exchange-rate", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketInfoDto.ExchangeRateResponse>>() {});
    }

    @Override
    public TossMarketInfoDto.KrMarketCalendarResponse getKrMarketCalendar(String date) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (date != null) {
            q.add("date", date);
        }
        return client.get("/api/v1/market-calendar/KR", q,
                new ParameterizedTypeReference<TossEnvelope<TossMarketInfoDto.KrMarketCalendarResponse>>() {});
    }

    @Override
    public TossMarketInfoDto.UsMarketCalendarResponse getUsMarketCalendar(String date) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (date != null) {
            q.add("date", date);
        }
        return client.get("/api/v1/market-calendar/US", q,
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
