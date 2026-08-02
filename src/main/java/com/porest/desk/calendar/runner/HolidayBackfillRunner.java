package com.porest.desk.calendar.runner;

import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.service.HolidaySyncService;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import com.porest.desk.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 기동 시 과거 연도를 한 번에 채운다.
 *
 * <p>초기 적재 전용이라 기본은 꺼져 있다. desk 는 로그인 여부 외의 권한 구분이 없어 수동 트리거 API 를
 * 두면 아무 사용자나 외부 API 트래픽을 태울 수 있으므로, 백필은 설정으로만 연다.
 * 적재가 끝나면 {@code HOLIDAY_BACKFILL_ENABLED} 를 다시 false 로 내리면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.holiday.backfill", name = "enabled", havingValue = "true")
public class HolidayBackfillRunner implements ApplicationRunner {

    private final HolidaySyncService holidaySyncService;
    private final ServiceClock serviceClock;
    private final HolidayProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        int startYear = properties.getBackfill().getStartYear();
        int endYear = serviceClock.today().getYear() + Math.max(0, properties.getSync().getLookaheadYears());

        log.info("공휴일 백필 시작: {}~{}년", startYear, endYear);
        List<HolidaySyncResult> results = holidaySyncService.syncRange(startYear, endYear);

        int inserted = results.stream().mapToInt(HolidaySyncResult::inserted).sum();
        int updated = results.stream().mapToInt(HolidaySyncResult::updated).sum();
        int removed = results.stream().mapToInt(HolidaySyncResult::removed).sum();
        long failed = results.stream().filter(HolidaySyncResult::isFailed).count();

        log.info("공휴일 백필 완료: {}~{}년, 추가={}, 수정={}, 삭제={}, 실패연도={}건",
            startYear, endYear, inserted, updated, removed, failed);
        log.info("백필이 끝났으면 app.holiday.backfill.enabled 를 false 로 되돌리세요.");
    }
}
