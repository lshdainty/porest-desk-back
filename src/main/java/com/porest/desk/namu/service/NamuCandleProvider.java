package com.porest.desk.namu.service;

import com.porest.desk.securities.service.SecuritiesCandleProvider;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.type.SecuritiesBroker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 나무증권 캔들 제공자.
 *
 * <p>나무는 <b>국내({@code /krstock/quote/v1/period})와 해외({@code /gbstock/quote/v1/period})가
 * 엔드포인트부터 파라미터 뜻까지 다르다.</b> 그 차이는 {@link NamuQueryService} 가 흡수하고
 * 여기서는 넘기기만 한다 — 자세한 함정은 {@code NamuQueryServiceImpl} 의 주기구분 상수 주석 참고.
 */
@Component
@RequiredArgsConstructor
public class NamuCandleProvider implements SecuritiesCandleProvider {

    private final NamuQueryService namuQueryService;

    @Override
    public SecuritiesBroker broker() {
        return SecuritiesBroker.NAMU;
    }

    @Override
    public CandlePage getCandles(Long userRowId, CandleQuery query) {
        return namuQueryService.getCandles(userRowId, query);
    }
}
