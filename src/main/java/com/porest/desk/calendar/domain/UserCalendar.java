package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCalendar extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "calendar_name", nullable = false, length = 50)
    private String calendarName;

    @Column(name = "color", nullable = false, length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_default", nullable = false, length = 1)
    private YNType isDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_visible", nullable = false, length = 1)
    private YNType isVisible;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static UserCalendar createCalendar(User user, String calendarName, String color, Integer sortOrder, boolean isDefault) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.calendarName = calendarName;
        calendar.color = color != null ? color : "#3b82f6";
        calendar.sortOrder = sortOrder != null ? sortOrder : 0;
        calendar.isDefault = isDefault ? YNType.Y : YNType.N;
        calendar.isVisible = YNType.Y;
        calendar.isDeleted = YNType.N;
        return calendar;
    }

    public void updateCalendar(String calendarName, String color) {
        if (calendarName != null) {
            this.calendarName = calendarName;
        }
        if (color != null) {
            this.color = color;
        }
    }

    public void toggleVisibility() {
        this.isVisible = this.isVisible == YNType.Y ? YNType.N : YNType.Y;
    }

    public void deleteCalendar() {
        this.isDeleted = YNType.Y;
    }
}
