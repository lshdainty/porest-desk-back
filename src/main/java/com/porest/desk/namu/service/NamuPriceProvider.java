package com.porest.desk.namu.service;

import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.dto.InstrumentRef;
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
 * <p><b>환율은 두 경로에서 얻는다.</b> 나무는 환율 전용 조회가 없고 지수·환율 통합 API 의
 * 코드값이 공개 문서에 없다. 대신 해외 잔고의 당일매매기준환율을 1순위로,
 * 해외 현재가의 {@code currency_prc} 를 2순위로 쓴다 — 2순위는 <b>계좌가 없어도</b> 얻는다.
 * 둘 다 실패할 때만 외화 평가를 접는다(부분합으로 금액을 왜곡하지 않는 기존 규칙 그대로).
 *
 * <p>예전엔 잔고가 유일한 경로인 줄 알고 <b>해외 계좌가 없는 사용자의 외화 평가를 통째로
 * 접었다.</b> 순서와 근거는 {@link NamuQueryService#getFxRate} 참고.
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
    public List<PriceQuote> getPrices(Long userRowId, List<InstrumentRef> instruments) {
        return instruments.isEmpty() ? List.of() : namuQueryService.getPrices(userRowId, instruments);
    }

    /**
     * {@code base}→{@code quote} 환율. 나무가 주는 건 <b>외화→원화</b> 기준환율뿐이라
     * quote 가 KRW 가 아니면 돌려줄 값이 없다.
     */
    @Override
    public BigDecimal getFxRate(Long userRowId, String baseCurrency, String quoteCurrency) {
        if (!"KRW".equalsIgnoreCase(quoteCurrency)) {
            log.debug("나무증권은 원화 환산 환율만 준다 - 건너뛴다: {}→{}", baseCurrency, quoteCurrency);
            return null;
        }
        return namuQueryService.getFxRate(userRowId, baseCurrency);
    }
}
