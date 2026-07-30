package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.HolidayServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface HolidayService {

    /**
     * 기간 내 공휴일을 조회한다.
     *
     * <p>데이터는 {@link HolidaySyncService} 가 외부 소스와 맞춘 결과라 조회 전용이다.
     */
    List<HolidayServiceDto.HolidayInfo> getHolidays(LocalDate startDate, LocalDate endDate);
}
