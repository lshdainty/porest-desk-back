package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.type.HolidayType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class HolidayServiceDto {

    public record CreateCommand(
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring
    ) {}

    public record UpdateCommand(
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring
    ) {}

    public record HolidayInfo(
        Long rowId,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        YNType isRecurring,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static HolidayInfo from(Holiday holiday) {
            return new HolidayInfo(
                holiday.getRowId(),
                holiday.getHolidayDate(),
                holiday.getHolidayName(),
                holiday.getHolidayType(),
                holiday.getIsRecurring(),
                holiday.getCreateAt(),
                holiday.getModifyAt()
            );
        }

        public static HolidayInfo virtual(Holiday holiday, LocalDate targetDate) {
            return new HolidayInfo(
                holiday.getRowId(),
                targetDate,
                holiday.getHolidayName(),
                holiday.getHolidayType(),
                holiday.getIsRecurring(),
                holiday.getCreateAt(),
                holiday.getModifyAt()
            );
        }
    }
}
