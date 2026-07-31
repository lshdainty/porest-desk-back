package com.porest.desk.stock.service;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시장 1개를 마스터파일과 맞춘다. 시장마다 트랜잭션을 끊어 한 시장의 실패가 다른 시장을 되돌리지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMarketSynchronizer {

    private final StockMasterRepository stockMasterRepository;

    /**
     * (market, symbol) 키로 대조해 실제로 달라진 행만 손댄다.
     *
     * <p>전량 삭제 후 재적재하면 변경이 없는 날에도 row_id 와 수정 이력이 매일 갱신되고,
     * 자산이 참조할 마스터 행이 순간적으로 사라지므로 diff upsert 로만 맞춘다.
     */
    @Transactional
    public StockMasterSyncResult sync(StockMarket market, List<KisStockRecord> records) {
        // 파일이 비정상(빈 응답)일 때 전 종목을 비활성화하면 안 된다. 기존 데이터를 유지한다.
        if (records.isEmpty()) {
            log.warn("종목 마스터 동기화 - 파일에 데이터 없음, 기존 데이터 유지: market={}", market);
            return StockMasterSyncResult.failed(market);
        }

        Map<String, StockMaster> existingBySymbol = new HashMap<>();
        List<StockMaster> existing = stockMasterRepository.findAllByMarketIncludingInactive(market);
        for (StockMaster stock : existing) {
            existingBySymbol.put(stock.getSymbol(), stock);
        }

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        Set<String> fileSymbols = new HashSet<>();

        for (KisStockRecord record : records) {
            fileSymbols.add(record.symbol());
            StockMaster stock = existingBySymbol.get(record.symbol());

            if (stock == null) {
                stockMasterRepository.save(StockMaster.create(market, record));
                inserted++;
                continue;
            }
            // 운영자가 지운 행은 되살리지 않는다. 유니크 제약 때문에 재적재도 할 수 없다.
            if (stock.isDeleted()) {
                unchanged++;
                continue;
            }
            if (stock.syncFrom(record)) {
                updated++;
            } else {
                unchanged++;
            }
        }

        int deactivated = 0;
        for (StockMaster stock : existing) {
            if (stock.isDeleted() || !stock.isActive() || fileSymbols.contains(stock.getSymbol())) {
                continue;
            }
            // 파일에서 사라진 종목(상장폐지·심볼 변경 등). 자산 연결 보호를 위해 행은 남긴다.
            stock.deactivate();
            deactivated++;
        }

        StockMasterSyncResult result = new StockMasterSyncResult(market, false, inserted, updated, deactivated, unchanged);
        if (result.hasChanges()) {
            log.info("종목 마스터 동기화 변경: market={}, 추가={}, 수정={}, 비활성={}", market, inserted, updated, deactivated);
        } else {
            log.debug("종목 마스터 동기화 변경 없음: market={}, 대상={}건", market, unchanged);
        }
        return result;
    }
}
