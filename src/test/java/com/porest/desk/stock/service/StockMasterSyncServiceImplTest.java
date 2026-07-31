package com.porest.desk.stock.service;

import com.porest.desk.stock.client.KisMasterFileClient;
import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.exception.KisMasterFileException;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 전체 동기화 오케스트레이션 테스트.
 *
 * <p>15개 시장을 순회하며, 한 시장의 다운로드 실패가 나머지 시장 동기화를 막으면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class StockMasterSyncServiceImplTest {

    @Mock private KisMasterFileClient kisMasterFileClient;
    @Mock private StockMarketSynchronizer marketSynchronizer;
    @InjectMocks private StockMasterSyncServiceImpl syncService;

    private final List<KisStockRecord> records =
        List.of(new KisStockRecord("AAPL", null, "NASAAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD"));

    @Test
    @DisplayName("15개 시장 전체를 순회하며 동기화한다")
    void syncsAllMarkets() {
        given(kisMasterFileClient.fetch(any(StockMarket.class))).willReturn(records);
        given(marketSynchronizer.sync(any(StockMarket.class), anyList()))
            .willAnswer(inv -> new StockMasterSyncResult(inv.getArgument(0), false, 1, 0, 0, 0));

        List<StockMasterSyncResult> results = syncService.syncAll();

        assertThat(results).hasSize(StockMarket.values().length);
        assertThat(results).allMatch(r -> !r.failed());
        for (StockMarket market : StockMarket.values()) {
            verify(kisMasterFileClient).fetch(market);
        }
    }

    @Test
    @DisplayName("한 시장의 다운로드 실패는 실패로 집계하고 나머지 시장은 계속 진행한다")
    void isolatesMarketFailure() {
        given(kisMasterFileClient.fetch(any(StockMarket.class))).willReturn(records);
        willThrow(new KisMasterFileException("다운로드 실패"))
            .given(kisMasterFileClient).fetch(StockMarket.NAS);
        given(marketSynchronizer.sync(any(StockMarket.class), anyList()))
            .willAnswer(inv -> new StockMasterSyncResult(inv.getArgument(0), false, 0, 0, 0, 1));

        List<StockMasterSyncResult> results = syncService.syncAll();

        assertThat(results).hasSize(StockMarket.values().length);
        assertThat(results.stream().filter(StockMasterSyncResult::failed))
            .singleElement()
            .extracting(StockMasterSyncResult::market)
            .isEqualTo(StockMarket.NAS);
        // 실패한 시장 뒤의 시장도 전부 동기화됐다.
        verify(kisMasterFileClient).fetch(StockMarket.HSX);
        verify(marketSynchronizer).sync(eq(StockMarket.HSX), anyList());
    }
}
