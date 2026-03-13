package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HolidayServiceImpl implements HolidayService {
    private final HolidayRepository holidayRepository;

    @Override
    @Transactional
    public HolidayServiceDto.HolidayInfo createHoliday(HolidayServiceDto.CreateCommand command) {
        log.debug("공휴일 등록 시작: date={}, name={}", command.holidayDate(), command.holidayName());

        Holiday holiday = Holiday.create(
            command.holidayDate(),
            command.holidayName(),
            command.holidayType(),
            command.isRecurring()
        );

        holidayRepository.save(holiday);

        log.info("공휴일 등록 완료: holidayId={}", holiday.getRowId());

        return HolidayServiceDto.HolidayInfo.from(holiday);
    }

    @Override
    public List<HolidayServiceDto.HolidayInfo> getHolidays(LocalDate startDate, LocalDate endDate) {
        log.debug("공휴일 목록 조회: startDate={}, endDate={}", startDate, endDate);

        List<HolidayServiceDto.HolidayInfo> result = new ArrayList<>();

        // 비반복 공휴일 조회
        List<Holiday> nonRecurring = holidayRepository.findByDateRange(startDate, endDate);
        for (Holiday h : nonRecurring) {
            result.add(HolidayServiceDto.HolidayInfo.from(h));
        }

        // 반복 공휴일 조회 - 조회 범위 내 연도에 가상 엔트리 생성
        // 동일한 (월, 일, 공휴일명)이 여러 연도에 존재할 수 있으므로 중복 제거
        List<Holiday> recurring = holidayRepository.findAllRecurring();
        Set<String> processedRecurring = new HashSet<>();
        for (Holiday h : recurring) {
            int month = h.getHolidayDate().getMonthValue();
            int day = h.getHolidayDate().getDayOfMonth();
            String key = month + "-" + day + "-" + h.getHolidayName();

            if (!processedRecurring.add(key)) {
                continue; // 이미 처리된 (월, 일, 공휴일명) 조합은 건너뜀
            }

            for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
                try {
                    LocalDate targetDate = LocalDate.of(year, month, day);
                    if (!targetDate.isBefore(startDate) && !targetDate.isAfter(endDate)) {
                        result.add(HolidayServiceDto.HolidayInfo.virtual(h, targetDate));
                    }
                } catch (java.time.DateTimeException e) {
                    // 2월 29일 같은 날짜가 해당 연도에 존재하지 않는 경우 무시
                    log.debug("반복 공휴일 날짜 생성 실패: year={}, month={}, day={}", year, month, day);
                }
            }
        }

        result.sort(Comparator.comparing(HolidayServiceDto.HolidayInfo::holidayDate));

        return result;
    }

    @Override
    @Transactional
    public HolidayServiceDto.HolidayInfo updateHoliday(Long holidayId, HolidayServiceDto.UpdateCommand command) {
        log.debug("공휴일 수정 시작: holidayId={}", holidayId);

        Holiday holiday = findHolidayOrThrow(holidayId);

        holiday.update(
            command.holidayDate(),
            command.holidayName(),
            command.holidayType(),
            command.isRecurring()
        );

        log.info("공휴일 수정 완료: holidayId={}", holidayId);

        return HolidayServiceDto.HolidayInfo.from(holiday);
    }

    @Override
    @Transactional
    public void deleteHoliday(Long holidayId) {
        log.debug("공휴일 삭제 시작: holidayId={}", holidayId);

        Holiday holiday = findHolidayOrThrow(holidayId);
        holiday.delete();

        log.info("공휴일 삭제 완료: holidayId={}", holidayId);
    }

    private Holiday findHolidayOrThrow(Long holidayId) {
        return holidayRepository.findById(holidayId)
            .orElseThrow(() -> {
                log.warn("공휴일 조회 실패 - 존재하지 않는 공휴일: holidayId={}", holidayId);
                return new EntityNotFoundException(DeskErrorCode.HOLIDAY_NOT_FOUND);
            });
    }
}
