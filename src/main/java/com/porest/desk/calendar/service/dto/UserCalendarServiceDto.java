package com.porest.desk.calendar.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.type.CalendarRole;

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
        Long ownerRowId,
        String ownerName,
        String calendarName,
        String color,
        Integer sortOrder,
        boolean isDefault,
        boolean isVisible,
        String inviteCode,
        boolean isShared,
        boolean isOwner,
        CalendarRole myRole,
        int memberCount,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static CalendarInfo from(UserCalendar calendar, CalendarRole myRole, int memberCount) {
            return new CalendarInfo(
                calendar.getRowId(),
                calendar.getUser().getRowId(),
                calendar.getUser().getUserName(),
                calendar.getCalendarName(),
                calendar.getColor(),
                calendar.getSortOrder(),
                calendar.getIsDefault() == YNType.Y,
                calendar.getIsVisible() == YNType.Y,
                calendar.getInviteCode(),
                memberCount > 1,
                myRole == CalendarRole.OWNER,
                myRole,
                memberCount,
                calendar.getCreateAt(),
                calendar.getModifyAt()
            );
        }

        /** 생성/수정 등 단일 캘린더 반환 컨텍스트 — 소유자 기준. */
        public static CalendarInfo from(UserCalendar calendar) {
            return from(calendar, CalendarRole.OWNER, 1);
        }
    }

    public record MemberInfo(
        Long rowId,
        Long userRowId,
        String userName,
        String userEmail,
        CalendarRole permission,
        LocalDateTime joinedAt
    ) {
        public static MemberInfo from(UserCalendarMember member) {
            return new MemberInfo(
                member.getRowId(),
                member.getUser().getRowId(),
                member.getUser().getUserName(),
                member.getUser().getUserEmail(),
                member.getPermission(),
                member.getJoinedAt()
            );
        }
    }
}
