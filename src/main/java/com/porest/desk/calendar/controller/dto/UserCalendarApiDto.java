package com.porest.desk.calendar.controller.dto;

import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.calendar.type.CalendarRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class UserCalendarApiDto {

    @Schema(name = "UserCalendarCreateRequest")
    public record CreateRequest(
        String calendarName,
        String color
    ) {}

    @Schema(name = "UserCalendarUpdateRequest")
    public record UpdateRequest(
        String calendarName,
        String color
    ) {}

    public record JoinRequest(
        String inviteCode
    ) {}

    public record ChangeRoleRequest(
        CalendarRole permission
    ) {}

    @Schema(name = "UserCalendarResponse")
    public record Response(
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
        int memberCount
    ) {
        public static Response from(UserCalendarServiceDto.CalendarInfo info) {
            return new Response(
                info.rowId(),
                info.ownerRowId(),
                info.ownerName(),
                info.calendarName(),
                info.color(),
                info.sortOrder(),
                info.isDefault(),
                info.isVisible(),
                info.inviteCode(),
                info.isShared(),
                info.isOwner(),
                info.myRole(),
                info.memberCount()
            );
        }
    }

    @Schema(name = "UserCalendarListResponse")
    public record ListResponse(
        List<Response> calendars
    ) {
        public static ListResponse from(List<UserCalendarServiceDto.CalendarInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }

    public record MemberResponse(
        Long rowId,
        Long userRowId,
        String userName,
        String userEmail,
        CalendarRole permission,
        LocalDateTime joinedAt
    ) {
        public static MemberResponse from(UserCalendarServiceDto.MemberInfo info) {
            return new MemberResponse(
                info.rowId(),
                info.userRowId(),
                info.userName(),
                info.userEmail(),
                info.permission(),
                info.joinedAt()
            );
        }
    }

    public record MemberListResponse(
        List<MemberResponse> members
    ) {
        public static MemberListResponse from(List<UserCalendarServiceDto.MemberInfo> infos) {
            return new MemberListResponse(infos.stream().map(MemberResponse::from).toList());
        }
    }

    public record InviteCodeResponse(
        String inviteCode
    ) {}
}
