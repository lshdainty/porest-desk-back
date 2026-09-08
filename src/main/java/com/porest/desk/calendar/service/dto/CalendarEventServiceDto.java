package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.patch.Patch;

import java.time.LocalDateTime;
import java.util.List;

public class CalendarEventServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String location,
        String rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId
    ) {}

    /**
     * 수정 명령 — 널 허용 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}).
     *
     * <p>나머지 칸의 뜻은 종전 그대로다 — {@code title}·{@code eventType}·{@code isAllDay} 는
     * null 이면 유지(도메인 가드), {@code startDate}·{@code endDate} 는 늘 실리고,
     * {@code reminderMinutes}·{@code calendarRowId} 는 null 이면 손대지 않는다.
     */
    public record UpdateCommand(
        String title,
        Patch<String> description,
        CalendarEventType eventType,
        Patch<String> color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Patch<Long> labelRowId,
        Patch<String> location,
        Patch<String> rrule,
        List<Integer> reminderMinutes,
        Long calendarRowId
    ) {}

    public record EventInfo(
        Long rowId,
        Long userRowId,
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String labelName,
        String labelColor,
        String location,
        String rrule,
        Long recurrenceId,
        YNType isException,
        List<EventReminderServiceDto.ReminderInfo> reminders,
        Long calendarRowId,
        String calendarName,
        String calendarColor,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static EventInfo from(CalendarEvent event) {
            return from(event, List.of());
        }

        /**
         * 반복 전개용 발생(occurrence) 복제 — 시각만 그 회차로 바꾼 사본.
         * rowId 는 원본 그대로다: 발생을 눌러 수정·삭제하면 시리즈 원본에 걸린다
         * (회차 단위 예외는 아직 없다 — RecurrenceExpander 주석 참고).
         */
        public EventInfo withOccurrence(LocalDateTime occurrenceStart, LocalDateTime occurrenceEnd) {
            return new EventInfo(rowId, userRowId, title, description, eventType, color,
                occurrenceStart, occurrenceEnd, isAllDay, labelRowId, labelName, labelColor,
                location, rrule, recurrenceId, isException, reminders,
                calendarRowId, calendarName, calendarColor, createAt, modifyAt);
        }

        public static EventInfo from(CalendarEvent event, List<EventReminderServiceDto.ReminderInfo> reminders) {
            return new EventInfo(
                event.getRowId(),
                event.getUser().getRowId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getColor(),
                event.getStartDate(),
                event.getEndDate(),
                event.getIsAllDay(),
                event.getLabel() != null ? event.getLabel().getRowId() : null,
                event.getLabel() != null ? event.getLabel().getLabelName() : null,
                event.getLabel() != null ? event.getLabel().getColor() : null,
                event.getLocation(),
                event.getRrule(),
                event.getRecurrenceId(),
                event.getIsException(),
                reminders,
                event.getCalendar() != null ? event.getCalendar().getRowId() : null,
                event.getCalendar() != null ? event.getCalendar().getCalendarName() : null,
                event.getCalendar() != null ? event.getCalendar().getColor() : null,
                event.getCreateAt(),
                event.getModifyAt()
            );
        }
    }
}
