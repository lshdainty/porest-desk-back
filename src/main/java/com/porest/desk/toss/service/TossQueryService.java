package com.porest.desk.toss.service;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.dto.TossMarketInfoDto;
import com.porest.desk.toss.dto.TossStockDto;

import java.util.List;

/**
 * 토스증권 Open API 조회 서비스 (읽기 전용 프록시).<br>
 * 주문 등 계좌에 영향을 주는 기능은 1차 범위에서 제외한다.
 *
 * <p>토스 API는 시세·종목·시장정보도 발급된 access token 으로만 호출하며 권한 scope 구분이 없으므로
 * (OpenAPI security: oauth2ClientCredentials, scopes 비어있음), 모든 조회는 사용자 본인이 등록한
 * 개인 키로 대리 수행한다. 따라서 모든 메서드가 {@code userRowId} 를 받는다.</p>
 */
public interface TossQueryService {

    // === Market Data (시세) ===

    /** 호가 조회 */
    TossMarketDto.OrderbookResponse getOrderbook(Long userRowId, String symbol);

    /** 현재가 조회 (여러 종목은 콤마로 구분) */
    List<TossMarketDto.PriceResponse> getPrices(Long userRowId, String symbols);

    /** 최근 체결 내역 조회 */
    List<TossMarketDto.Trade> getTrades(Long userRowId, String symbol, Integer count);

    /** 상/하한가 조회 */
    TossMarketDto.PriceLimitResponse getPriceLimits(Long userRowId, String symbol);

    /**
     * 캔들 차트 조회 (interval: 1m | 1d). 커서 기반 단일 페이지 프록시.
     * <p>요청당 {@code size} 개(토스 상한 200)를 조회하며, 더 과거 페이지가 있으면
     * {@code CursorResponse.meta.nextCursor}(= 토스 nextBefore)를 다음 {@code cursor}로 전달한다.
     *
     * @param size   페이지 크기(null/0이하면 200, 200 초과 시 200으로 cap)
     * @param cursor 직전 페이지의 nextCursor(= 토스 before). 첫 페이지면 null
     */
    CursorResponse<TossMarketDto.Candle> getCandles(Long userRowId, String symbol, String interval, Integer size, String cursor, Boolean adjusted);

    // === Stock Info (종목 정보) ===

    /** 종목 기본 정보 조회 (여러 종목은 콤마로 구분) */
    List<TossStockDto.StockInfo> getStocks(Long userRowId, String symbols);

    /** 매수 유의사항 조회 */
    List<TossStockDto.StockWarning> getStockWarnings(Long userRowId, String symbol);

    // === Market Info (환율·장 일정) ===

    /** 환율 조회 */
    TossMarketInfoDto.ExchangeRateResponse getExchangeRate(Long userRowId, String baseCurrency, String quoteCurrency, String dateTime);

    /** 국내 장 운영 정보 조회 */
    TossMarketInfoDto.KrMarketCalendarResponse getKrMarketCalendar(Long userRowId, String date);

    /** 해외(미국) 장 운영 정보 조회 */
    TossMarketInfoDto.UsMarketCalendarResponse getUsMarketCalendar(Long userRowId, String date);

    // === Account / Asset (계좌·보유자산) ===

    /** 계좌 목록 조회 */
    List<TossAccountDto.Account> getAccounts(Long userRowId);

    /** 보유 주식 조회 (accountSeq = 토스증권 계좌 식별 키) */
    TossAccountDto.HoldingsOverview getHoldings(Long userRowId, Long accountSeq, String symbol);
}
