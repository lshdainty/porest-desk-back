package com.porest.desk.asset.scheduler;

import com.porest.desk.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 연결 투자 자산의 평가액을 하루 1회(장 마감 후 16시) VALUATION 스냅샷으로 적재한다.
 * 순자산 추이 그래프에 증권 변동이 반영되도록 하는 게 목적이며, 실시간 표시는 클라이언트
 * 라이브 오버레이가 담당한다(이 스케줄러는 추이용 1일 1행만 적재).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TossValuationSnapshotScheduler {
    private final AssetService assetService;

    @Scheduled(cron = "0 0 16 * * *")
    public void snapshotTossValuations() {
        log.info("토스 평가액 스냅샷 스케줄러 실행 시작");
        try {
            assetService.snapshotTossValuations();
            log.info("토스 평가액 스냅샷 스케줄러 실행 완료");
        } catch (Exception e) {
            log.error("토스 평가액 스냅샷 스케줄러 실행 중 오류 발생", e);
        }
    }
}
