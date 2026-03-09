package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.HolidayServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface HolidayService {
    HolidayServiceDto.HolidayInfo createHoliday(HolidayServiceDto.CreateCommand command);
    List<HolidayServiceDto.HolidayInfo> getHolidays(LocalDate startDate, LocalDate endDate);
    HolidayServiceDto.HolidayInfo updateHoliday(Long holidayId, HolidayServiceDto.UpdateCommand command);
    void deleteHoliday(Long holidayId);
}
