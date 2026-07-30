package com.porest.desk.calendar.service;

import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HolidayServiceImpl implements HolidayService {
    private final HolidayRepository holidayRepository;

    @Override
    public List<HolidayServiceDto.HolidayInfo> getHolidays(LocalDate startDate, LocalDate endDate) {
        log.debug("공휴일 목록 조회: startDate={}, endDate={}", startDate, endDate);

        return holidayRepository.findByDateRange(startDate, endDate).stream()
            .map(HolidayServiceDto.HolidayInfo::from)
            .toList();
    }
}
