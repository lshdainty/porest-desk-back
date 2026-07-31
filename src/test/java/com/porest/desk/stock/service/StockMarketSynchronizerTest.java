package com.porest.desk.stock.service;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 시장 동기화 diff 로직 테스트.
 *
 * <p>전량 삭제 후 재적재하면 자산이 참조할 마스터 행이 순간적으로 사라지고 수정 이력이 매일 갱신되므로,
 * 실제로 달라진 행만 손대야 한다. 파일이 비정상(빈 응답)일 때 전 종목을 비활성화해 버리면
 * 검색이 통째로 비므로 "빈 파일 = 유지"가 보장돼야 한다.
 */
@ExtendWith(MockitoExtension.class)
class StockMarketSynchronizerTest {

    @Mock private StockMasterRepository stockMasterRepository;
    @InjectMocks private StockMarketSynchronizer synchronizer;

    private KisStockRecord record(String symbol, String nameKr) {
        return new KisStockRecord(symbol, null, "NAS" + symbol, nameKr, nameKr + " INC", StockSecurityType.STOCK, "USD");
    }

    private StockMaster existing(String symbol, String nameKr) {
        return StockMaster.create(StockMarket.NAS, record(symbol, nameKr));
    }

    @Test
    @DisplayName("DB 에 없는 종목은 새로 적재한다")
    void insertsNewStock() {
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of());

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        ArgumentCaptor<StockMaster> captor = ArgumentCaptor.forClass(StockMaster.class);
        verify(stockMasterRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(captor.getValue().getMarketCode()).isEqualTo(StockMarket.NAS);
        assertThat(captor.getValue().getCountryCode()).isEqualTo("US");
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.failed()).isFalse();
    }

    @Test
    @DisplayName("파일 값이 그대로면 아무것도 갱신하지 않는다 — 3만여 행의 수정 이력 오염 방지")
    void keepsUnchangedStock() {
        StockMaster apple = existing("AAPL", "애플");
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of(apple));

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        verify(stockMasterRepository, never()).save(any());
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("종목명이 바뀐 종목은 갱신으로 집계한다")
    void updatesChangedStock() {
        StockMaster apple = existing("AAPL", "애플컴퓨터");
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of(apple));

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        assertThat(apple.getNameKr()).isEqualTo("애플");
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("파일에서 사라진 종목은 행을 지우지 않고 비활성 처리한다 — 자산 연결 보호")
    void deactivatesMissingStock() {
        StockMaster delisted = existing("GONE", "상장폐지");
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of(delisted));

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        assertThat(delisted.isActive()).isFalse();
        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("파일에 다시 나타난 비활성 종목은 되살린다 — 재상장·일시 누락 복구")
    void reactivatesReturnedStock() {
        StockMaster apple = existing("AAPL", "애플");
        apple.deactivate();
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of(apple));

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        assertThat(apple.isActive()).isTrue();
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 파일이면 기존 데이터를 건드리지 않고 실패로 집계한다")
    void keepsExistingWhenFileEmpty() {
        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of());

        verify(stockMasterRepository, never()).findAllByMarketIncludingInactive(any());
        verify(stockMasterRepository, never()).save(any());
        assertThat(result.failed()).isTrue();
    }

    @Test
    @DisplayName("운영자가 지운 행은 파일에 있어도 되살리지 않는다")
    void keepsDeletedRowUntouched() {
        StockMaster deleted = existing("AAPL", "애플");
        org.springframework.test.util.ReflectionTestUtils.setField(deleted, "isDeleted", com.porest.core.type.YNType.Y);
        given(stockMasterRepository.findAllByMarketIncludingInactive(StockMarket.NAS)).willReturn(List.of(deleted));

        StockMasterSyncResult result = synchronizer.sync(StockMarket.NAS, List.of(record("AAPL", "애플")));

        verify(stockMasterRepository, never()).save(any());
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
    }
}
