package com.porest.desk.stock.runner;

import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.StockMasterSyncService;
import com.porest.desk.stock.service.dto.StockMasterSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기동 시 종목 마스터가 비어 있으면 한 번에 채운다.
 *
 * <p>스케줄러만 있으면 첫 배포 후 다음 날 아침까지 검색이 빈 채로 돌게 된다. 공휴일 백필과 달리
 * 인증·호출 한도가 없는 공개 파일 15개 다운로드가 전부라 기본으로 켜 두고,
 * 이미 데이터가 있으면 count 1회만 하고 그대로 끝난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kis.master.backfill", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockMasterBackfillRunner implements ApplicationRunner {

    private final StockMasterRepository stockMasterRepository;
    private final StockMasterSyncService stockMasterSyncService;

    @Override
    public void run(ApplicationArguments args) {
        long count = stockMasterRepository.countAll();
        if (count > 0) {
            log.debug("종목 마스터 백필 생략 - 이미 적재됨: {}건", count);
            return;
        }

        log.info("종목 마스터 백필 시작 (테이블 비어 있음)");
        List<StockMasterSyncResult> results = stockMasterSyncService.syncAll();

        int inserted = results.stream().mapToInt(StockMasterSyncResult::inserted).sum();
        long failed = results.stream().filter(StockMasterSyncResult::failed).count();
        log.info("종목 마스터 백필 완료: 적재={}건, 실패={}시장", inserted, failed);
    }
}
