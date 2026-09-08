package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
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
import jakarta.persistence.Version;
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
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
    @JoinColumn(name = "calendar_row_id")
    private UserCalendar calendar;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static CalendarEvent createEvent(User user, String title, String description,
            CalendarEventType eventType, String color, LocalDateTime startDate, LocalDateTime endDate,
            YNType isAllDay, EventLabel label, String location, String rrule, UserCalendar calendar) {
        CalendarEvent event = new CalendarEvent();
        event.user = user;
        event.title = title;
        event.description = description;
        // eventType 은 NOT NULL 이다. 종전엔 null 을 그대로 내려보내 저장이 409 로 튕겼고,
        // 그 409 는 "다른 곳에서 먼저 수정됐어요" 라고 말했다(QA #81). 화면이 뜻을 정해 주지
        // 않은 일정은 개인 일정이다 — 웹도 새 일정에 PERSONAL 을 박아 보낸다.
        event.eventType = eventType != null ? eventType : CalendarEventType.PERSONAL;
        event.color = color != null ? color : "#2c70bf";
        event.startDate = startDate;
        event.endDate = endDate;
        event.isAllDay = isAllDay != null ? isAllDay : YNType.N;
        event.label = label;
        event.location = location;
        event.rrule = rrule;
        event.calendar = calendar;
        event.isException = YNType.N;
        event.isDeleted = YNType.N;
        return event;
    }

    /**
     * 수정. <b>NOT NULL 세 칸({@code title}·{@code eventType}·{@code isAllDay})은 값이 오지 않으면
     * 기존 값을 지킨다</b> — 생성과 달리 여기엔 이미 사용자가 정한 값이 있으므로, 기본값을 씌우면
     * 고치지 않은 칸이 조용히 바뀐다(WORK 일정을 PERSONAL 로 되돌리는 식). 종전엔 null 을 그대로
     * 덮어써 저장이 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다(QA #81).
     *
     * <p>널 허용 칸({@code description}·{@code color}·{@code label}·{@code location}·{@code rrule})은
     * 반대로 그대로 덮는다 — 거기서 null 은 "지운다" 는 뜻이고, 지울 방법을 없애면 안 된다.
     * <b>"안 보낸 칸은 유지" 판단은 서비스가 한다</b>(QA #96) — 여기 오는 다섯 칸은 이미
     * 병합이 끝난 값이다. 이 자리에서 다시 null 을 무시하면 지울 방법이 없어진다.
     */
    public void updateEvent(String title, String description, CalendarEventType eventType,
            String color, LocalDateTime startDate, LocalDateTime endDate, YNType isAllDay,
            EventLabel label, String location, String rrule) {
        if (title != null) this.title = title;
        if (eventType != null) this.eventType = eventType;
        if (isAllDay != null) this.isAllDay = isAllDay;
        this.description = description;
        this.color = color;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public void setCalendar(UserCalendar calendar) {
        this.calendar = calendar;
    }

    public void deleteEvent() {
        this.isDeleted = YNType.Y;
    }
}
