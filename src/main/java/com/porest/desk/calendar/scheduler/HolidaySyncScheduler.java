package com.porest.desk.calendar.scheduler;

import com.porest.desk.calendar.service.HolidaySyncService;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대한민국 공휴일을 외부 소스와 매일 맞춘다.
 *
 * <p>연말에 다음 해를 한 번 넣어 두는 방식은 연중 제도 변경을 놓친다. 2026년만 해도 제헌절이
 * 공휴일로 재지정됐고(2026-02-10 공포) 근로자의 날이 노동절 법정공휴일로 바뀌었다(2026-03-31 가결).
 * 임시공휴일은 지정에서 시행까지 2주 남짓인 사례도 있어 하루 1회 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.holiday.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HolidaySyncScheduler {

    private final HolidaySyncService holidaySyncService;

    @Scheduled(cron = "${app.holiday.sync.cron:0 0 12 * * *}")
    public void sync() {
        log.debug("공휴일 동기화 시작");
        List<HolidaySyncResult> results = holidaySyncService.syncUpcoming();

        long failed = results.stream().filter(HolidaySyncResult::isFailed).count();
        if (failed > 0) {
            log.error("공휴일 동기화 일부 실패: 전체={}건, 실패={}건", results.size(), failed);
        }

        // 변경이 없는 날이 대부분이라 실제 변경이 있을 때만 결과를 남긴다.
        results.stream()
            .filter(HolidaySyncResult::hasChanges)
            .forEach(r -> log.info("공휴일 동기화 완료: year={}, 추가={}, 수정={}, 삭제={}",
                r.year(), r.inserted(), r.updated(), r.removed()));
    }
}
