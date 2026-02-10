package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.EventReminder;

import java.time.LocalDateTime;

public class EventReminderServiceDto {

    public record CreateCommand(
        Long eventRowId,
        String reminderType,
        Integer minutesBefore
    ) {}

    public record ReminderInfo(
        Long rowId,
        Long eventRowId,
        String reminderType,
        Integer minutesBefore,
        YNType isSent,
        LocalDateTime sentAt
    ) {
        public static ReminderInfo from(EventReminder reminder) {
            return new ReminderInfo(
                reminder.getRowId(),
                reminder.getEvent().getRowId(),
                reminder.getReminderType(),
                reminder.getMinutesBefore(),
                reminder.getIsSent(),
                reminder.getSentAt()
            );
        }
    }
}
