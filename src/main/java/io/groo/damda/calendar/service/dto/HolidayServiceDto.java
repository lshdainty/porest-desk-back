package io.groo.damda.calendar.service.dto;

import io.groo.damda.calendar.domain.Holiday;
import io.groo.damda.calendar.type.HolidayType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class HolidayServiceDto {

    public record HolidayInfo(
        Long rowId,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static HolidayInfo from(Holiday holiday) {
            return new HolidayInfo(
                holiday.getRowId(),
                holiday.getHolidayDate(),
                holiday.getHolidayName(),
                holiday.getHolidayType(),
                holiday.getCreateAt(),
                holiday.getModifyAt()
            );
        }
    }
}
