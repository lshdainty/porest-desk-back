package com.porest.desk.securities.service;

import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.type.SecuritiesBroker;

import java.math.BigDecimal;
import java.util.List;

/**
 * 가계부 자산 평가가 증권사에 요구하는 <b>전부</b>. 현재가와 환율 둘뿐이다.
 *
 * <p><b>왜 이만큼만 공통인가</b> — 증권 화면이 보여주는 것(랭킹·시장지표·호가·체결추이·
 * 투자자별 매매)은 증권사마다 있는 것과 없는 것이 달라서, 합집합 인터페이스를 만들면 절반이
 * {@code UnsupportedOperationException} 이 된다. 그건 추상화가 아니라 거짓말이다.
 * 그래서 증권 조회는 {@code /api/v1/toss/**} · {@code /api/v1/namu/**} 로 나눠 두고,
 * <b>두 증권사가 똑같이 주는 것</b>만 이 인터페이스 뒤로 넣었다.
 *
 * <p>사용자가 고른 기본 소스가 어느 증권사인지는 {@link SecuritiesPriceProviders} 가 정한다.
 */
public interface SecuritiesPriceProvider {

    SecuritiesBroker broker();

    /**
     * 종목 다건 현재가. <b>못 구한 종목은 결과에서 빠진다</b>(예외 아님) —
     * 호출부가 빠진 종목을 보고 평가를 접을지 정한다.
     *
     * <p>심볼이 아니라 {@link InstrumentRef}(시장 + 심볼)를 받는다. 같은 티커가 여러 시장에
     * 걸리는 경우가 많아 심볼만으로는 종목이 안 정해진다 — 자세한 이유는 InstrumentRef 주석.
     * 돌아오는 {@link PriceQuote#symbol()} 은 요청한 심볼 그대로다.
     */
    List<PriceQuote> getPrices(Long userRowId, List<InstrumentRef> instruments);

    /** 환율. 못 구하면 null — 외화 자산은 평가를 접는다(부분합으로 금액을 왜곡하지 않는다). */
    BigDecimal getFxRate(Long userRowId, String baseCurrency, String quoteCurrency);
}
