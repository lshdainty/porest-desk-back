package com.porest.desk.toss.service;

import com.porest.core.controller.dto.CursorResponse;
import com.porest.desk.toss.client.TossApiClient;
import com.porest.desk.toss.dto.TossMarketDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 캔들 커서 프록시 매핑 — 토스 CandlePageResponse(candles/nextBefore) →
 * porest-core CursorResponse(content/meta.nextCursor), size cap(≤200), cursor→before 전달 검증.
 */
@ExtendWith(MockitoExtension.class)
class TossQueryServiceImplTest {

    @Mock private TossApiClient client;
    @InjectMocks private TossQueryServiceImpl sut;

    private static final long USER = 1L;

    private static TossMarketDto.Candle candle(String ts) {
        return new TossMarketDto.Candle(ts, "100", "110", "90", "105", "1000", "KRW");
    }

    @Test
    @DisplayName("토스 candles/nextBefore 를 CursorResponse content/nextCursor 로 매핑(nextBefore 있으면 hasNext)")
    void getCandles_mapsToCursorResponse_withNextCursor() {
        TossMarketDto.CandlePageResponse page =
                new TossMarketDto.CandlePageResponse(List.of(candle("t1"), candle("t2")), "c123");
        given(client.<TossMarketDto.CandlePageResponse>get(eq(USER), eq("/api/v1/candles"), any(), any()))
                .willReturn(page);

        CursorResponse<TossMarketDto.Candle> result =
                sut.getCandles(USER, "005930", "1d", 200, null, null);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getMeta().isHasNext()).isTrue();
        assertThat(result.getMeta().getNextCursor()).isEqualTo("c123");
        assertThat(result.getMeta().getSize()).isEqualTo(200);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isEqualTo("200");
        assertThat(q.getValue().getFirst("before")).isNull();
    }

    @Test
    @DisplayName("nextBefore 가 null 이면 마지막 페이지 — hasNext=false, nextCursor=null")
    void getCandles_lastPage_noNextCursor() {
        TossMarketDto.CandlePageResponse page =
                new TossMarketDto.CandlePageResponse(List.of(candle("t1")), null);
        given(client.<TossMarketDto.CandlePageResponse>get(eq(USER), eq("/api/v1/candles"), any(), any()))
                .willReturn(page);

        CursorResponse<TossMarketDto.Candle> result =
                sut.getCandles(USER, "005930", "1d", 100, null, null);

        assertThat(result.getMeta().isHasNext()).isFalse();
        assertThat(result.getMeta().getNextCursor()).isNull();
    }

    @Test
    @DisplayName("size 200 초과/미지정은 토스 상한 200 으로 cap")
    void getCandles_capsSizeTo200() {
        sut.getCandles(USER, "005930", "1m", 390, null, null);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("count")).isEqualTo("200");
    }

    @Test
    @DisplayName("cursor 는 토스 before 파라미터로 전달(시간 역방향 다음 페이지)")
    void getCandles_passesCursorAsBefore() {
        sut.getCandles(USER, "005930", "1d", 200, "c999", null);

        ArgumentCaptor<MultiValueMap<String, String>> q = queryCaptor();
        verify(client).get(eq(USER), eq("/api/v1/candles"), q.capture(), any());
        assertThat(q.getValue().getFirst("before")).isEqualTo("c999");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<MultiValueMap<String, String>> queryCaptor() {
        return ArgumentCaptor.forClass(MultiValueMap.class);
    }
}
