package com.porest.desk.calendar.service;

import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 연도 동기화를 묶어 실행한다.
 *
 * <p>트랜잭션은 {@link HolidayYearSynchronizer} 가 연도 단위로 잡으므로 여기서는 열지 않는다.
 * 한 해가 실패해도 나머지 연도는 그대로 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidaySyncServiceImpl implements HolidaySyncService {

    private final HolidayYearSynchronizer yearSynchronizer;
    private final HolidayProperties properties;

    @Override
    public List<HolidaySyncResult> syncUpcoming() {
        int currentYear = LocalDate.now().getYear();
        int lookahead = Math.max(0, properties.getSync().getLookaheadYears());
        return syncRange(currentYear, currentYear + lookahead);
    }

    @Override
    public List<HolidaySyncResult> syncRange(int startYear, int endYear) {
        if (startYear > endYear) {
            log.warn("공휴일 동기화 구간이 올바르지 않습니다: startYear={}, endYear={}", startYear, endYear);
            return List.of();
        }

        List<HolidaySyncResult> results = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            try {
                results.add(yearSynchronizer.sync(year));
            } catch (RuntimeException e) {
                // 한 해의 DB 오류가 나머지 연도까지 막지 않도록 삼킨다.
                log.error("공휴일 동기화 중 오류: year={}", year, e);
                results.add(HolidaySyncResult.failed(year));
            }
        }
        return results;
    }
}
