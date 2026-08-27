package com.porest.desk.securities.service;

import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.type.SecuritiesBroker;

/**
 * 캔들(기간별시세)을 줄 수 있는 증권사.
 *
 * <h2>왜 {@link SecuritiesPriceProvider} 에 메서드를 얹지 않았나</h2>
 *
 * <p>그쪽 주석이 이미 답을 적어 뒀다 — "합집합 인터페이스를 만들면 절반이
 * {@code UnsupportedOperationException} 이 된다. 그건 추상화가 아니라 거짓말이다."
 * 현재가·환율은 <b>연동한 증권사면 반드시 준다</b>(자산 평가가 성립하려면 그래야 한다).
 * 캔들은 다르다 — 차트가 없는 증권사, 계좌 API 만 여는 증권사가 얼마든지 있을 수 있다.
 *
 * <p>그래서 <b>인터페이스를 나눈다.</b> 미지원은 메서드가 던지는 예외가 아니라
 * <b>이 인터페이스를 구현하지 않는 것</b>으로 표현한다. 지원 여부를 물어보는 별도 플래그도
 * 두지 않는다 — 플래그와 구현이 어긋날 수 있는 자리를 아예 만들지 않는 편이 낫다.
 * 누가 캔들을 줄 수 있는지는 {@link SecuritiesCandleProviders} 가 기동 시 한 번 세어 로그로 남긴다.
 *
 * <p>제공자를 늘리는 법 — 구현에 {@code @Component} 를 달면 자동 등록된다.
 * {@link SecuritiesPriceProviders} 와 달리 <b>전 증권사 커버리지를 강제하지 않는다.</b>
 */
public interface SecuritiesCandleProvider {

    SecuritiesBroker broker();

    /**
     * 캔들 한 페이지. 시간 오름차순으로 돌려준다.
     *
     * <p><b>빈 페이지는 오류가 아니다</b> — 상장 직후 종목, 장 시작 전, 조회 범위 밖은
     * 정상적으로 0건이다. 호출부는 빈 차트를 그린다.
     *
     * @throws com.porest.core.exception.InvalidValueException  종목을 특정할 수 없을 때
     * @throws com.porest.core.exception.ExternalServiceException 증권사 호출이 실패했을 때
     */
    CandlePage getCandles(Long userRowId, CandleQuery query);
}
