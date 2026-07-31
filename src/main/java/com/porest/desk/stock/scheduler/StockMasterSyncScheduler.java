package com.porest.desk.stock.scheduler;

import com.porest.desk.stock.service.StockMasterSyncService;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주식 종목 마스터를 KIS 마스터파일과 매일 맞춘다.
 *
 * <p>신규상장·상장폐지·종목명 변경을 따라가기 위해서다. 시세와 달리 장중 변동이 없는 데이터라
 * 하루 1회면 충분하고, 미국장 마감(한국 새벽)과 국내장 개장 사이인 아침에 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kis.master.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockMasterSyncScheduler {

    private final StockMasterSyncService stockMasterSyncService;

    @Scheduled(cron = "${app.kis.master.sync.cron:0 0 7 * * *}")
    public void sync() {
        log.debug("종목 마스터 동기화 시작");
        List<StockMasterSyncResult> results = stockMasterSyncService.syncAll();

        long failed = results.stream().filter(StockMasterSyncResult::failed).count();
        if (failed > 0) {
            log.error("종목 마스터 동기화 일부 실패: 전체={}시장, 실패={}시장", results.size(), failed);
        }

        // 변경이 없는 날이 대부분이라 실제 변경이 있을 때만 결과를 남긴다.
        results.stream()
            .filter(StockMasterSyncResult::hasChanges)
            .forEach(r -> log.info("종목 마스터 동기화 완료: market={}, 추가={}, 수정={}, 비활성={}",
                r.market(), r.inserted(), r.updated(), r.deactivated()));
    }
}
