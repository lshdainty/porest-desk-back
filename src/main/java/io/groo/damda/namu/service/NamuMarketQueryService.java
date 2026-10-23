package io.groo.damda.namu.service;

import io.groo.damda.namu.dto.NamuQuoteDto;
import io.groo.damda.securities.service.dto.InvestorTradingDay;
import io.groo.damda.toss.dto.TossMarketDto;

import java.util.List;

/**
 * 나무증권 종목 상세 시세 · 체결 · 투자자별 · 시간외 · ETF · 증거금 조회 (13b-0, 읽기 전용).
 *
 * <p>모든 호출은 <b>그 사람 본인 키로 그 사람 것만</b> 부른다. 캐시도 키에 사용자를 넣는다 —
 * 사용자끼리 나누는 캐시나 묶음 조회는 없다.
 */
public interface NamuMarketQueryService {

    /**
     * 국내 종목 상세 시세. 일반 종목은 {@code currentPrice}({@code market_cd=UNT}) 한 번 + EPS 용
     * 기간별 시세(6시간 캐시), ETF 는 {@code etfCurrent} 한 번이다. 사용자 × 종목 20초 캐시.
     */
    NamuQuoteDto.Detail getKrQuote(Long userRowId, String symbol);

    /** 미국 종목 상세 시세 ({@code /gbstock/quote/v1/current}). 사용자 × 종목 20초 캐시. */
    NamuQuoteDto.Detail getGbQuote(Long userRowId, String symbol);

    /** 국내 최근 체결 — 토스 {@code Trade} 와 같은 이름, 최신이 먼저. 캐시 없음. */
    List<TossMarketDto.Trade> getKrExecutions(Long userRowId, String symbol, Integer count);

    /** 미국 최근 체결 — 시각은 거래소 현지 시각으로 읽는다(문서에 시간대가 없다). 캐시 없음. */
    List<TossMarketDto.Trade> getGbExecutions(Long userRowId, String symbol, Integer count);

    /** 국내 투자자별 매매동향 — 미국 종목이면 빈 목록. 캐시 없음. */
    List<InvestorTradingDay> getKrInvestors(Long userRowId, String symbol, Integer count);

    /** ETF 구성 종목 — 비중 큰 순. 캐시 없음. */
    List<NamuQuoteDto.EtfComponent> getEtfComponents(Long userRowId, String symbol);

    /** 국내 시간외 단일가. 사용자 × 종목 20초 캐시. */
    NamuQuoteDto.AfterHours getKrAfterHours(Long userRowId, String symbol);

    /** 해외 증거금의 USD 행. 화면 줄 이름은 "예수금 · 달러". */
    NamuQuoteDto.Margin getMargin(Long userRowId);
}
