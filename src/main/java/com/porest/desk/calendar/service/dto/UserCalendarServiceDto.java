package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.UserCalendar;

import java.time.LocalDateTime;

public class UserCalendarServiceDto {

    public record CreateCommand(
        Long userRowId,
        String calendarName,
        String color
    ) {}

    public record UpdateCommand(
        String calendarName,
        String color
    ) {}

    public record CalendarInfo(
        Long rowId,
        Long userRowId,
        String calendarName,
        String color,
        Integer sortOrder,
        boolean isDefault,
        boolean isVisible,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static CalendarInfo from(UserCalendar calendar) {
            return new CalendarInfo(
                calendar.getRowId(),
                calendar.getUser().getRowId(),
                calendar.getCalendarName(),
                calendar.getColor(),
                calendar.getSortOrder(),
                calendar.getIsDefault() == YNType.Y,
                calendar.getIsVisible() == YNType.Y,
                calendar.getCreateAt(),
                calendar.getModifyAt()
            );
        }
    }
}
