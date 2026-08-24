package com.porest.desk.stock.service;

import com.porest.desk.stock.client.MasterFileClient;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.exception.KisMasterFileException;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.MasterFile;
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
 * <p>파일을 순회하며, 한 파일의 다운로드 실패가 나머지 동기화를 막으면 안 된다.
 * NH 는 KIS 가 만든 행을 보강하므로 KIS 뒤에 도는 순서도 함께 지킨다.
 */
@ExtendWith(MockitoExtension.class)
class StockMasterSyncServiceImplTest {

    @Mock private MasterFileClient masterFileClient;
    @Mock private MasterFileSynchronizer fileSynchronizer;
    @InjectMocks private StockMasterSyncServiceImpl syncService;

    private final List<InstrumentRecord> records = List.of(InstrumentRecord.kis(
        StockMarket.NAS, "AAPL", null, "NASAAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD"));

    @Test
    @DisplayName("등록된 마스터파일 전체를 순회하며 동기화한다")
    void syncsAllFiles() {
        given(masterFileClient.fetch(any(MasterFile.class))).willReturn(records);
        given(fileSynchronizer.sync(any(MasterFile.class), anyList()))
            .willAnswer(inv -> new StockMasterSyncResult(inv.getArgument(0), false, 1, 0, 0, 0));

        List<StockMasterSyncResult> results = syncService.syncAll();

        assertThat(results).hasSize(MasterFile.values().length);
        assertThat(results).allMatch(r -> !r.failed());
        for (MasterFile file : MasterFile.values()) {
            verify(masterFileClient).fetch(file);
        }
        // NH 는 KIS 가 만든 행을 찾아 보강한다 — 먼저 돌면 붙일 행이 없다.
        assertThat(MasterFile.values()[MasterFile.values().length - 1].getSource())
            .isEqualTo(com.porest.desk.stock.type.MasterSource.NH);
    }

    @Test
    @DisplayName("한 파일의 다운로드 실패는 실패로 집계하고 나머지는 계속 진행한다")
    void isolatesFileFailure() {
        given(masterFileClient.fetch(any(MasterFile.class))).willReturn(records);
        willThrow(new KisMasterFileException("다운로드 실패"))
            .given(masterFileClient).fetch(MasterFile.KIS_NAS);
        given(fileSynchronizer.sync(any(MasterFile.class), anyList()))
            .willAnswer(inv -> new StockMasterSyncResult(inv.getArgument(0), false, 0, 0, 0, 1));

        List<StockMasterSyncResult> results = syncService.syncAll();

        assertThat(results).hasSize(MasterFile.values().length);
        assertThat(results.stream().filter(StockMasterSyncResult::failed))
            .singleElement()
            .extracting(StockMasterSyncResult::file)
            .isEqualTo(MasterFile.KIS_NAS);
        // 실패한 파일 뒤의 파일도 전부 동기화됐다.
        verify(masterFileClient).fetch(MasterFile.NH_OVERSEAS);
        verify(fileSynchronizer).sync(eq(MasterFile.NH_OVERSEAS), anyList());
    }
}
