package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.type.CalendarRole;
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

import java.time.LocalDateTime;

/**
 * 공유 캘린더 멤버. 캘린더(calendar_row_id)에 참여한 사용자(user_row_id)의 권한(permission)을 담는다.
 * 소유자도 permission=OWNER 행 1개로 표현해 멤버 조회를 단일화한다 (UserGroupMember 패턴 미러).
 */
@Entity
@Table(name = "user_calendar_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCalendarMember extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_row_id", nullable = false)
    private UserCalendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    private CalendarRole permission;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static UserCalendarMember create(UserCalendar calendar, User user, CalendarRole permission) {
        UserCalendarMember member = new UserCalendarMember();
        member.calendar = calendar;
        member.user = user;
        member.permission = permission;
        member.joinedAt = LocalDateTime.now();
        member.isDeleted = YNType.N;
        return member;
    }

    public void changePermission(CalendarRole permission) {
        this.permission = permission;
    }

    public void removeMember() {
        this.isDeleted = YNType.Y;
    }
}
