package com.porest.desk.calendar.service;

import com.porest.desk.calendar.client.HolidayProvider;
import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.exception.HolidayProviderException;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import com.porest.desk.calendar.type.HolidaySource;
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
 * 연도 1건을 외부 소스와 맞춘다. 연도마다 트랜잭션을 끊어 한 해의 실패가 다른 해를 되돌리지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayYearSynchronizer {

    private final List<HolidayProvider> providers;
    private final HolidayRepository holidayRepository;

    /**
     * 우선순위대로 소스를 시도해 첫 성공분으로 해당 연도를 맞춘다.
     *
     * <p>전량 삭제 후 재적재하면 변경이 없는 날에도 수정 이력과 row_id 가 매일 갱신되므로,
     * (날짜, 이름) 키로 대조해 실제로 달라진 행만 손댄다.
     */
    @Transactional
    public HolidaySyncResult sync(int year) {
        Fetched fetched = fetch(year);
        if (fetched == null) {
            return HolidaySyncResult.failed(year);
        }

        // 소스가 해당 연도를 커버하지 않는 경우(빈 응답)에 기존 데이터를 지우면 안 된다.
        if (fetched.holidays().isEmpty()) {
            log.info("공휴일 동기화 - 소스에 데이터 없음, 기존 데이터 유지: year={}, source={}", year, fetched.source());
            return new HolidaySyncResult(year, fetched.source(), 0, 0, 0, 0);
        }

        Map<String, Holiday> existingByKey = new HashMap<>();
        List<Holiday> existing = holidayRepository.findByYearIncludingDeleted(year);
        for (Holiday holiday : existing) {
            existingByKey.put(keyOf(holiday), holiday);
        }

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        Set<String> externalKeys = new HashSet<>();

        for (ExternalHoliday external : fetched.holidays()) {
            externalKeys.add(external.key());
            Holiday holiday = existingByKey.get(external.key());

            if (holiday == null) {
                holidayRepository.save(Holiday.create(
                    external.holidayDate(),
                    external.holidayName(),
                    external.holidayType(),
                    fetched.source()
                ));
                inserted++;
                continue;
            }

            // 사용자가 지운 공휴일은 되살리지 않는다. 유니크 제약 때문에 재적재도 할 수 없다.
            if (holiday.isDeleted()) {
                unchanged++;
                continue;
            }

            if (holiday.getHolidayType() != external.holidayType()) {
                holiday.syncType(external.holidayType());
                updated++;
            } else {
                unchanged++;
            }
        }

        int removed = 0;
        for (Holiday holiday : existing) {
            if (holiday.isDeleted() || !holiday.isSyncManaged() || externalKeys.contains(keyOf(holiday))) {
                continue;
            }
            // 외부 소스에서 사라진 공휴일(제도 변경·이름 변경 등)
            holiday.delete();
            removed++;
        }

        HolidaySyncResult result = new HolidaySyncResult(year, fetched.source(), inserted, updated, removed, unchanged);
        if (result.hasChanges()) {
            log.info("공휴일 동기화 변경: year={}, source={}, 추가={}, 수정={}, 삭제={}",
                year, fetched.source(), inserted, updated, removed);
        } else {
            log.debug("공휴일 동기화 변경 없음: year={}, source={}, 대상={}건", year, fetched.source(), unchanged);
        }
        return result;
    }

    /** 등록된 소스를 순서대로 시도한다. 모두 실패하면 null. */
    private Fetched fetch(int year) {
        for (HolidayProvider provider : providers) {
            try {
                return new Fetched(provider.source(), provider.fetch(year));
            } catch (HolidayProviderException e) {
                log.warn("공휴일 소스 호출 실패, 다음 소스로 넘어갑니다: year={}, source={}, reason={}",
                    year, provider.source(), e.getMessage());
            }
        }
        log.error("공휴일 동기화 실패 - 모든 소스 호출 실패: year={}", year);
        return null;
    }

    private String keyOf(Holiday holiday) {
        return holiday.getHolidayDate() + "|" + holiday.getHolidayName();
    }

    private record Fetched(HolidaySource source, List<ExternalHoliday> holidays) {
    }
}
