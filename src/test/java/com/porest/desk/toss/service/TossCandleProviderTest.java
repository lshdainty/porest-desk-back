package com.porest.desk.toss.service;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.SecuritiesCandle;
import com.porest.desk.securities.type.CandleInterval;
import com.porest.desk.securities.type.SecuritiesBroker;
import com.porest.desk.toss.dto.TossMarketDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 토스 캔들을 증권사 무관 모양으로 옮긴다.
 *
 * <p><b>토스 차트는 실사용 화면이라 동작이 바뀌면 안 된다.</b> 그래서 여기서 보는 것은
 * "값이 그대로 흐르는가" 와 "커서를 우리가 해석하지 않는가" 다 — 토스 커서는 불투명
 * 문자열이고, 우리가 손대면 토스가 형식을 바꾸는 날 조용히 깨진다.
 */
@ExtendWith(MockitoExtension.class)
class TossCandleProviderTest {

    @Mock private TossQueryService tossQueryService;

    @Test
    @DisplayName("토스가 준 값과 커서를 그대로 옮긴다")
    void mapsCandlesAndCursor() {
        given(tossQueryService.getCandles(eq(1L), eq("005930"), eq("1d"), eq(200), eq(null), eq(null)))
            .willReturn(CursorResponse.of(List.of(
                    new TossMarketDto.Candle("2026-08-26T00:00:00+09:00",
                        "69000", "70500", "68800", "70000", "12345678", "KRW")),
                200, true, "opaque-toss-cursor"));

        TossCandleProvider sut = new TossCandleProvider(tossQueryService);
        CandlePage page = sut.getCandles(1L,
            new CandleQuery("005930", CandleInterval.DAY_1, 200, null, null));

        assertThat(sut.broker()).isEqualTo(SecuritiesBroker.TOSS);
        assertThat(page.candles()).containsExactly(new SecuritiesCandle(
            "2026-08-26T00:00:00+09:00", "69000", "70500", "68800", "70000", "12345678", "KRW"));
        // 커서는 토스가 준 문자열 그대로 — 우리가 해석하지 않는다.
        assertThat(page.nextCursor()).isEqualTo("opaque-toss-cursor");
    }

    @Test
    @DisplayName("주기·커서·수정주가를 토스 어휘 그대로 넘긴다")
    void passesQueryThrough() {
        given(tossQueryService.getCandles(eq(1L), eq("AAPL"), eq("1m"), eq(50), eq("cur"), eq(true)))
            .willReturn(CursorResponse.of(List.of(), 50, false, (String) null));

        new TossCandleProvider(tossQueryService).getCandles(1L,
            new CandleQuery("AAPL", CandleInterval.MINUTE_1, 50, "cur", true));

        verify(tossQueryService).getCandles(1L, "AAPL", "1m", 50, "cur", true);
    }

    @Test
    @DisplayName("빈 페이지도 오류가 아니다 — 커서 없이 끝난다")
    void emptyPage() {
        given(tossQueryService.getCandles(eq(1L), eq("005930"), eq("1d"), eq(200), eq(null), eq(null)))
            .willReturn(CursorResponse.of(List.of(), 200, false, (String) null));

        CandlePage page = new TossCandleProvider(tossQueryService).getCandles(1L,
            new CandleQuery("005930", CandleInterval.DAY_1, 200, null, null));

        assertThat(page.candles()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}
