package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private CalendarEventType eventType;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_all_day", nullable = false, length = 1)
    private YNType isAllDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_row_id")
    private EventLabel label;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "rrule", length = 500)
    private String rrule;

    @Column(name = "recurrence_id")
    private Long recurrenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_exception", nullable = false, length = 1)
    private YNType isException;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_row_id")
    private UserGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static CalendarEvent createEvent(User user, String title, String description,
            CalendarEventType eventType, String color, LocalDateTime startDate, LocalDateTime endDate,
            YNType isAllDay, EventLabel label, String location, String rrule) {
        CalendarEvent event = new CalendarEvent();
        event.user = user;
        event.title = title;
        event.description = description;
        event.eventType = eventType;
        event.color = color != null ? color : "#3B82F6";
        event.startDate = startDate;
        event.endDate = endDate;
        event.isAllDay = isAllDay != null ? isAllDay : YNType.N;
        event.label = label;
        event.location = location;
        event.rrule = rrule;
        event.isException = YNType.N;
        event.isDeleted = YNType.N;
        return event;
    }

    public void updateEvent(String title, String description, CalendarEventType eventType,
            String color, LocalDateTime startDate, LocalDateTime endDate, YNType isAllDay,
            EventLabel label, String location, String rrule) {
        this.title = title;
        this.description = description;
        this.eventType = eventType;
        this.color = color;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isAllDay = isAllDay;
        this.label = label;
        this.location = location;
        this.rrule = rrule;
    }

    public void setRecurrenceId(Long recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public void markAsException() {
        this.isException = YNType.Y;
    }

    public void setGroup(UserGroup group) {
        this.group = group;
    }

    public void deleteEvent() {
        this.isDeleted = YNType.Y;
    }
}
