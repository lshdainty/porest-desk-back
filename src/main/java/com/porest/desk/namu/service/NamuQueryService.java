package com.porest.desk.namu.service;

import com.porest.desk.securities.service.dto.PriceQuote;

import java.util.List;

/**
 * 나무증권 Open API 조회 서비스 (읽기 전용 프록시).
 *
 * <p>주문·정정·취소는 범위에서 제외한다 — 나무는 실제 체결이 나가는 운영 도메인 하나뿐이라
 * 잘못 부르면 되돌릴 수 없다.
 *
 * <p><b>토스와 엔드포인트 집합이 겹치지 않는다.</b> 나무엔 랭킹·시장지표가 없고 토스엔
 * 체결추이·투자자별·채권·금현물이 없다. 그래서 조회는 통합하지 않고 증권사별로 둔다
 * (증권 화면도 {@code /stocks/toss} · {@code /stocks/namu} 로 나뉜다).
 * 가계부 자산이 쓰는 현재가만 {@code SecuritiesPriceProvider} 뒤로 공통화했다.
 */
public interface NamuQueryService {

    /**
     * 국내주식 현재가. 종목코드 6자리.
     *
     * @param marketCode 거래소 구분 {@code KRX}(기본) · {@code NXT} · {@code UNT}.
     *                   종목이 NXT 거래 대상인지는 {@code stock_master.nxt_tradable} 이 안다
     */
    PriceQuote getKrPrice(Long userRowId, String symbol, String marketCode);

    /** 해외주식 현재가. 티커. */
    PriceQuote getGbPrice(Long userRowId, String symbol);

    /** 국내·해외를 섞어 다건 조회. 못 구한 종목은 결과에서 빠진다. */
    List<PriceQuote> getPrices(Long userRowId, List<String> symbols);
}
