package com.porest.desk.namu.service;

import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;

import java.math.BigDecimal;
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
    List<PriceQuote> getPrices(Long userRowId, List<InstrumentRef> instruments);

    // === 계좌·잔고 ===

    /** 본인 계좌 목록. 잔고 조회의 {@code act_no} 를 여기서 얻는다. */
    List<NamuAccountDto.Account> getAccounts(Long userRowId);

    /**
     * 보유 종목. 국내와 해외는 엔드포인트도 필드명도 달라 서비스가 한 모양으로 옮긴다.
     *
     * @param accountNo 계좌번호. null 이면 첫 계좌를 쓴다
     * @param currency  통화. {@code KRW} 면 국내, 그 밖(USD·CNY·HKD·JPY)이면 해외 해당 통화
     */
    NamuAccountDto.Holdings getHoldings(Long userRowId, String accountNo, String currency);

    /**
     * 원화 환산 환율.
     *
     * <p><b>나무는 환율 전용 조회가 없다.</b> 지수·환율 통합 API 는 있지만 {@code iem_cd} 에
     * 넣을 환율 코드가 공개 문서 어디에도 없다. 대신 해외 잔고 응답에 당일매매기준환율
     * ({@code tdt_sby_bse_xcg_rt})이 실려 오므로 그걸 쓴다 — 문서화된 유일한 경로다.
     *
     * <p>그래서 <b>해외 계좌가 없으면 환율도 못 구한다</b>(null). 호출부는 외화 평가를 접는다.
     */
    BigDecimal getFxRate(Long userRowId, String currency);
}
