package com.porest.desk.toss.service;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.securities.service.SecuritiesCandleProvider;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.SecuritiesCandle;
import com.porest.desk.securities.type.SecuritiesBroker;
import com.porest.desk.toss.dto.TossMarketDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 토스증권 캔들 제공자.
 *
 * <p><b>기존 {@code /api/v1/toss/candles} 와 같은 코드를 탄다.</b> 여기서 하는 일은
 * 필드 이름을 증권사 무관 이름으로 옮기는 것뿐이라, 토스 차트의 동작(페이지 크기 상한 ·
 * 커서 규약 · 수정주가)은 바뀌지 않는다. 실사용 화면이라 바뀌면 안 된다.
 *
 * <p>커서는 <b>토스가 준 값을 그대로 통과시킨다</b>({@code nextBefore}). 우리가 해석하면
 * 토스가 형식을 바꾸는 날 조용히 깨진다.
 */
@Component
@RequiredArgsConstructor
public class TossCandleProvider implements SecuritiesCandleProvider {

    private final TossQueryService tossQueryService;

    @Override
    public SecuritiesBroker broker() {
        return SecuritiesBroker.TOSS;
    }

    @Override
    public CandlePage getCandles(Long userRowId, CandleQuery query) {
        CursorResponse<TossMarketDto.Candle> page = tossQueryService.getCandles(
            userRowId, query.symbol(), query.interval().getCode(),
            query.size(), query.cursor(), query.adjusted());

        List<SecuritiesCandle> candles = page.getContent() == null ? List.of()
            : page.getContent().stream().map(TossCandleProvider::toCandle).toList();
        String nextCursor = page.getMeta() == null ? null : page.getMeta().getNextCursor();
        return new CandlePage(candles, nextCursor);
    }

    private static SecuritiesCandle toCandle(TossMarketDto.Candle c) {
        return new SecuritiesCandle(c.timestamp(), c.openPrice(), c.highPrice(),
            c.lowPrice(), c.closePrice(), c.volume(), c.currency());
    }
}
