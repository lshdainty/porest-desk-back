package com.porest.desk.namu.service;

import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.type.SecuritiesBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 나무증권 시세 제공자.
 *
 * <p><b>환율은 아직 못 준다.</b> 나무의 환율은 해외주식 시세 응답에 딸린 종목지수환율이라
 * 독립 조회가 없다. 그래서 {@code null} 을 돌려주고, 자산 평가는 외화 종목이 섞이면 평가를
 * 접는다(부분합으로 금액을 왜곡하지 않는 기존 규칙 그대로). 나무를 기본 소스로 고른 사용자가
 * 외화 자산을 가지면 여기서 막히므로, 환율 소스를 붙이는 게 다음 과제다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamuPriceProvider implements SecuritiesPriceProvider {

    private final NamuQueryService namuQueryService;

    @Override
    public SecuritiesBroker broker() {
        return SecuritiesBroker.NAMU;
    }

    @Override
    public List<PriceQuote> getPrices(Long userRowId, List<String> symbols) {
        return symbols.isEmpty() ? List.of() : namuQueryService.getPrices(userRowId, symbols);
    }

    @Override
    public BigDecimal getFxRate(Long userRowId, String baseCurrency, String quoteCurrency) {
        log.debug("나무증권 환율 미지원 - 외화 평가를 건너뛴다: {}→{}", baseCurrency, quoteCurrency);
        return null;
    }
}
