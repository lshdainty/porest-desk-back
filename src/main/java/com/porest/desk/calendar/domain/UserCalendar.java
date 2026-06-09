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

import java.util.UUID;

@Entity
@Table(name = "user_calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCalendar extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    // 캘린더 소유자(생성자). 공유 시에도 owner 역할.
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

    // 공유용 초대 코드(생성 시 자동 발급, 공유받은 사람이 입력해 참여).
    @Column(name = "invite_code", length = 20, unique = true)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static UserCalendar createCalendar(User user, String calendarName, String color, Integer sortOrder, boolean isDefault) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.calendarName = calendarName;
        calendar.color = color != null ? color : "#2c70bf";
        calendar.sortOrder = sortOrder != null ? sortOrder : 0;
        calendar.isDefault = isDefault ? YNType.Y : YNType.N;
        calendar.isVisible = YNType.Y;
        calendar.inviteCode = generateInviteCode();
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

    public String regenerateInviteCode() {
        this.inviteCode = generateInviteCode();
        return this.inviteCode;
    }

    public void deleteCalendar() {
        this.isDeleted = YNType.Y;
    }

    private static String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
