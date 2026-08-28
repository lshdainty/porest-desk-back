package com.porest.desk.namu.service;

import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
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

    // === 캔들(기간별시세) ===

    /**
     * 캔들 한 페이지. 시간 오름차순.
     *
     * <p>국내({@code /krstock/quote/v1/period})와 해외({@code /gbstock/quote/v1/period})는
     * 엔드포인트도, 파라미터 이름도, <b>같은 주기를 가리키는 숫자도</b> 다르다. 어느 쪽으로
     * 갈지는 {@code stock_master} 의 국가코드가 정한다 — 시세({@link #getPrices})와 같은 규칙이다.
     *
     * <p><b>종목당 1콜이다.</b> 나무엔 다건 캔들 API 가 없고 429 한도가 있어, 구현이 짧은
     * 메모리 캐시를 둔다(기간 탭을 빠르게 눌러도 상류로는 한 번만 나간다).
     *
     * @throws com.porest.core.exception.InvalidValueException 종목 마스터에 없는 심볼일 때
     *         ({@code SECURITIES_SYMBOL_INVALID}). 시세는 조용히 건너뛰지만 여기는 <b>한 종목을
     *         보러 온 자리</b>라 빈 차트로 얼버무리지 않는다
     */
    CandlePage getCandles(Long userRowId, CandleQuery query);

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
     * 원화 환산 환율. <b>USD 만 지원한다</b>(나무 해외 연동이 미국 고정).
     *
     * <p><b>나무는 환율 전용 조회가 없다.</b> 지수·환율 통합 API 는 있지만 {@code iem_cd} 에
     * 넣을 환율 코드가 공개 문서 어디에도 없다. 대신 <b>두 군데에 환율이 딸려 온다</b>.
     *
     * <ol>
     *   <li>해외 잔고의 당일매매기준환율({@code tdt_sby_bse_xcg_rt}) — 계좌 평가에 실제
     *       적용된 값이라 화면과 일치한다. 단 <b>계좌 + 보유 종목</b>이 있어야 한다</li>
     *   <li>해외 현재가의 {@code currency_prc} — 종목코드 하나만 있으면 되므로
     *       <b>계좌가 없어도</b> 얻는다</li>
     * </ol>
     *
     * <p>1번을 먼저 쓰고 없으면 2번으로 넘어간다. 둘 다 실패해야 null 이고, 그때 호출부는
     * 외화 평가를 접는다.
     *
     * <p><b>예전 주석은 잔고가 "문서화된 유일한 경로" 라고 했는데 틀렸다</b> — 그래서 해외
     * 계좌가 없는 사용자가 환율을 영영 못 구했다. 근거는 구현부 주석 참고.
     */
    BigDecimal getFxRate(Long userRowId, String currency);
}
