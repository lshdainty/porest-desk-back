package com.porest.desk.toss.service;

import com.porest.desk.toss.dto.TossAccountDto;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.dto.TossMarketInfoDto;
import com.porest.desk.toss.dto.TossStockDto;

import java.util.List;

/**
 * 토스증권 Open API 조회 서비스 (읽기 전용 프록시).<br>
 * 주문 등 계좌에 영향을 주는 기능은 1차 범위에서 제외한다.
 */
public interface TossQueryService {

    // === Market Data (시세) ===

    /** 호가 조회 */
    TossMarketDto.OrderbookResponse getOrderbook(String symbol);

    /** 현재가 조회 (여러 종목은 콤마로 구분) */
    List<TossMarketDto.PriceResponse> getPrices(String symbols);

    /** 최근 체결 내역 조회 */
    List<TossMarketDto.Trade> getTrades(String symbol, Integer count);

    /** 상/하한가 조회 */
    TossMarketDto.PriceLimitResponse getPriceLimits(String symbol);

    /** 캔들 차트 조회 (interval: 1m | 1d) */
    TossMarketDto.CandlePageResponse getCandles(String symbol, String interval, Integer count, String before, Boolean adjusted);

    // === Stock Info (종목 정보) ===

    /** 종목 기본 정보 조회 (여러 종목은 콤마로 구분) */
    List<TossStockDto.StockInfo> getStocks(String symbols);

    /** 매수 유의사항 조회 */
    List<TossStockDto.StockWarning> getStockWarnings(String symbol);

    // === Market Info (환율·장 일정) ===

    /** 환율 조회 */
    TossMarketInfoDto.ExchangeRateResponse getExchangeRate(String baseCurrency, String quoteCurrency, String dateTime);

    /** 국내 장 운영 정보 조회 */
    TossMarketInfoDto.KrMarketCalendarResponse getKrMarketCalendar(String date);

    /** 해외(미국) 장 운영 정보 조회 */
    TossMarketInfoDto.UsMarketCalendarResponse getUsMarketCalendar(String date);

    // === Account / Asset (계좌·보유자산) ===

    /** 계좌 목록 조회 */
    List<TossAccountDto.Account> getAccounts();

    /** 보유 주식 조회 (accountSeq = 토스증권 계좌 식별 키) */
    TossAccountDto.HoldingsOverview getHoldings(Long accountSeq, String symbol);
}
