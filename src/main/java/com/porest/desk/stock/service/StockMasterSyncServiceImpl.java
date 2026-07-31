package com.porest.desk.stock.service;

import com.porest.desk.stock.client.KisMasterFileClient;
import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 시장 동기화를 묶어 실행한다.
 *
 * <p>트랜잭션은 {@link StockMarketSynchronizer} 가 시장 단위로 잡으므로 여기서는 열지 않는다.
 * 한 시장이 실패해도 나머지 시장은 그대로 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterSyncServiceImpl implements StockMasterSyncService {

    private final KisMasterFileClient kisMasterFileClient;
    private final StockMarketSynchronizer marketSynchronizer;

    @Override
    public List<StockMasterSyncResult> syncAll() {
        List<StockMasterSyncResult> results = new ArrayList<>();
        for (StockMarket market : StockMarket.values()) {
            try {
                List<KisStockRecord> records = kisMasterFileClient.fetch(market);
                results.add(marketSynchronizer.sync(market, records));
            } catch (RuntimeException e) {
                // 한 시장의 다운로드·DB 오류가 나머지 시장까지 막지 않도록 삼킨다.
                log.error("종목 마스터 동기화 중 오류: market={}", market, e);
                results.add(StockMasterSyncResult.failed(market));
            }
        }
        return results;
    }
}
