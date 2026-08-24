package com.porest.desk.toss.service;

import com.porest.desk.securities.service.SecuritiesPriceProvider;
import com.porest.desk.securities.service.dto.InstrumentRef;
import com.porest.desk.securities.service.dto.PriceQuote;
import com.porest.desk.securities.type.SecuritiesBroker;
import com.porest.desk.toss.dto.TossMarketDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 토스증권 시세 제공자. 조회 자체는 기존 {@link TossQueryService} 가 하고 여기서는
 * 증권사 무관 모양으로 옮기기만 한다.
 *
 * <p>토스는 종목 표기가 stock_master 심볼과 같아 변환이 없다. 시장코드도 안 쓴다 —
 * 토스가 다루는 시장(국내·미국)에서는 심볼이 갈리지 않아서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPriceProvider implements SecuritiesPriceProvider {

    private final TossQueryService tossQueryService;

    @Override
    public SecuritiesBroker broker() {
        return SecuritiesBroker.TOSS;
    }

    @Override
    public List<PriceQuote> getPrices(Long userRowId, List<InstrumentRef> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        String symbols = instruments.stream()
            .map(InstrumentRef::symbol)
            .filter(sym -> sym != null && !sym.isBlank())
            .distinct()
            .collect(java.util.stream.Collectors.joining(","));
        if (symbols.isEmpty()) {
            return List.of();
        }
        List<PriceQuote> quotes = new ArrayList<>();
        for (TossMarketDto.PriceResponse p : tossQueryService.getPrices(userRowId, symbols)) {
            BigDecimal price = parse(p.lastPrice());
            if (price != null) {
                // 토스 시세 응답엔 전일 종가가 없다. 캔들을 종목마다 따로 받아야 하는데
                // 평가액에는 안 쓰이는 값이라(등락 표시 전용) 여기서 N콜을 더 태우지 않는다.
                quotes.add(PriceQuote.of(p.symbol(), price, p.currency()));
            }
        }
        return quotes;
    }

    @Override
    public BigDecimal getFxRate(Long userRowId, String baseCurrency, String quoteCurrency) {
        return parse(tossQueryService.getExchangeRate(userRowId, baseCurrency, quoteCurrency, null).rate());
    }

    /** 토스는 가격을 문자열로 준다. 형식이 어긋나면 그 종목만 버린다. */
    private static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("토스 시세 파싱 실패: {}", raw);
            return null;
        }
    }
}
