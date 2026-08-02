package com.porest.desk.asset.scheduler;

import com.porest.desk.asset.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 연결 투자 자산의 평가액(시세 × 보유수량)을 하루 1회 VALUATION 앵커로 적재한다.
 *
 * <p>화면 표시는 라이브(비영속)로 실시간 갱신되지만, 순자산 추이 그래프에 증권 변동이
 * 남도록 장 마감 후 종가 기준 스냅샷을 종목당 하루 1행만 적재한다(1분마다 적재 금지).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TossValuationSnapshotScheduler {

    private final AssetService assetService;

    /** 매일 16:00 — 국내 장 마감(15:30) 이후 종가 기준 1회. */
    @Scheduled(cron = "0 0 16 * * *", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void snapshot() {
        log.info("토스 평가액 일일 스냅샷 시작");
        assetService.snapshotTossValuations();
        log.info("토스 평가액 일일 스냅샷 완료");
    }
}
