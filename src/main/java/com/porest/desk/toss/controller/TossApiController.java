package com.porest.desk.toss.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.dto.TossMarketInfoDto;
import com.porest.desk.toss.dto.TossStockDto;
import com.porest.desk.toss.service.TossQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 토스증권 Open API 조회 프록시 컨트롤러.<br>
 * 모든 엔드포인트는 인증된 사용자만 접근 가능하며(SecurityConfig 의 {@code anyRequest().authenticated()}),
 * 1차 범위로 <b>읽기 전용</b> 시세·종목·시장정보·계좌·보유자산 조회만 노출한다. 주문 기능은 제외.
 */
@RestController
@RequestMapping("/api/v1/toss")
@RequiredArgsConstructor
public class TossApiController {

    private final TossQueryService tossQueryService;

    // === Market Data (시세) ===

    @GetMapping("/orderbook")
    public ApiResponse<TossMarketDto.OrderbookResponse> getOrderbook(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol) {
        return ApiResponse.success(tossQueryService.getOrderbook(loginUser.getRowId(), symbol));
    }

    @GetMapping("/prices")
    public ApiResponse<List<TossMarketDto.PriceResponse>> getPrices(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbols) {
        return ApiResponse.success(tossQueryService.getPrices(loginUser.getRowId(), symbols));
    }

    @GetMapping("/trades")
    public ApiResponse<List<TossMarketDto.Trade>> getTrades(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol,
            @RequestParam(required = false) Integer count) {
        return ApiResponse.success(tossQueryService.getTrades(loginUser.getRowId(), symbol, count));
    }

    @GetMapping("/price-limits")
    public ApiResponse<TossMarketDto.PriceLimitResponse> getPriceLimits(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol) {
        return ApiResponse.success(tossQueryService.getPriceLimits(loginUser.getRowId(), symbol));
    }

    /**
     * 캔들 차트 조회. 커서 기반 단일 페이지(요청당 {@code size}개, 토스 상한 200).
     * 더 과거가 있으면 응답 {@code meta.nextCursor}를 다음 요청 {@code cursor}로 전달한다(시간 역방향 lazy-load).
     */
    @GetMapping("/candles")
    public ApiResponse<CursorResponse<TossMarketDto.Candle>> getCandles(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Boolean adjusted) {
        return ApiResponse.success(tossQueryService.getCandles(loginUser.getRowId(), symbol, interval, size, cursor, adjusted));
    }

    // === Stock Info (종목 정보) ===

    @GetMapping("/stocks")
    public ApiResponse<List<TossStockDto.StockInfo>> getStocks(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String symbols) {
        return ApiResponse.success(tossQueryService.getStocks(loginUser.getRowId(), symbols));
    }

    @GetMapping("/stocks/{symbol}/warnings")
    public ApiResponse<List<TossStockDto.StockWarning>> getStockWarnings(
            @LoginUser UserPrincipal loginUser,
            @PathVariable String symbol) {
        return ApiResponse.success(tossQueryService.getStockWarnings(loginUser.getRowId(), symbol));
    }

    // === Market Info (환율·장 일정) ===

    @GetMapping("/exchange-rate")
    public ApiResponse<TossMarketInfoDto.ExchangeRateResponse> getExchangeRate(
            @LoginUser UserPrincipal loginUser,
            @RequestParam String baseCurrency,
            @RequestParam String quoteCurrency,
            @RequestParam(required = false) String dateTime) {
        return ApiResponse.success(tossQueryService.getExchangeRate(loginUser.getRowId(), baseCurrency, quoteCurrency, dateTime));
    }

    @GetMapping("/market-calendar/KR")
    public ApiResponse<TossMarketInfoDto.KrMarketCalendarResponse> getKrMarketCalendar(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) String date) {
        return ApiResponse.success(tossQueryService.getKrMarketCalendar(loginUser.getRowId(), date));
    }

    @GetMapping("/market-calendar/US")
    public ApiResponse<TossMarketInfoDto.UsMarketCalendarResponse> getUsMarketCalendar(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) String date) {
        return ApiResponse.success(tossQueryService.getUsMarketCalendar(loginUser.getRowId(), date));
    }

    // === Account / Asset (계좌·보유자산) ===

    @GetMapping("/accounts")
    public ApiResponse<List<TossAccountDto.Account>> getAccounts(
            @LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(tossQueryService.getAccounts(loginUser.getRowId()));
    }

    @GetMapping("/holdings")
    public ApiResponse<TossAccountDto.HoldingsOverview> getHoldings(
            @LoginUser UserPrincipal loginUser,
            @RequestParam Long accountSeq,
            @RequestParam(required = false) String symbol) {
        return ApiResponse.success(tossQueryService.getHoldings(loginUser.getRowId(), accountSeq, symbol));
    }
}
