package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
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
@Table(name = "event_reminder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventReminder extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_row_id")
    private CalendarEvent event;

    @Column(name = "reminder_type", nullable = false, length = 20)
    private String reminderType;

    @Column(name = "minutes_before", nullable = false)
    private Integer minutesBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_sent", nullable = false, length = 1)
    private YNType isSent;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static EventReminder create(CalendarEvent event, String reminderType, Integer minutesBefore) {
        EventReminder reminder = new EventReminder();
        reminder.event = event;
        reminder.reminderType = reminderType != null ? reminderType : "NOTIFICATION";
        reminder.minutesBefore = minutesBefore;
        reminder.isSent = YNType.N;
        return reminder;
    }

    public void markSent() {
        this.isSent = YNType.Y;
        this.sentAt = LocalDateTime.now();
    }
}
