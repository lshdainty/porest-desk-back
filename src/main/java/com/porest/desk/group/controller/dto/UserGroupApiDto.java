package com.porest.desk.group.controller.dto;

import com.porest.desk.group.service.dto.UserGroupServiceDto;
import com.porest.desk.group.type.GroupRole;

import java.time.LocalDateTime;
import java.util.List;

public class UserGroupApiDto {

    public record CreateRequest(
        String groupName,
        String description,
        Long groupTypeId
    ) {}

    public record UpdateRequest(
        String groupName,
        String description,
        Long groupTypeId
    ) {}

    public record JoinRequest(
        String inviteCode
    ) {}

    public record ChangeRoleRequest(
        GroupRole role
    ) {}

    public record Response(
        Long rowId,
        String groupName,
        String description,
        Long groupTypeId,
        String groupTypeName,
        String groupTypeColor,
        String inviteCode,
        int memberCount,
        LocalDateTime createAt
    ) {
        public static Response from(UserGroupServiceDto.GroupInfo info) {
            return new Response(
                info.rowId(),
                info.groupName(),
                info.description(),
                info.groupTypeId(),
                info.groupTypeName(),
                info.groupTypeColor(),
                info.inviteCode(),
                info.memberCount(),
                info.createAt()
            );
        }
    }

    public record DetailResponse(
        Long rowId,
        String groupName,
        String description,
        Long groupTypeId,
        String groupTypeName,
        String groupTypeColor,
        String inviteCode,
        List<MemberResponse> members,
        LocalDateTime createAt
    ) {
        public static DetailResponse from(UserGroupServiceDto.GroupDetailInfo info) {
            return new DetailResponse(
                info.rowId(),
                info.groupName(),
                info.description(),
                info.groupTypeId(),
                info.groupTypeName(),
                info.groupTypeColor(),
                info.inviteCode(),
                info.members().stream().map(MemberResponse::from).toList(),
                info.createAt()
            );
        }
    }

    public record MemberResponse(
        Long rowId,
        Long userRowId,
        String userName,
        String userEmail,
        GroupRole role,
        LocalDateTime joinedAt
    ) {
        public static MemberResponse from(UserGroupServiceDto.MemberInfo info) {
            return new MemberResponse(
                info.rowId(),
                info.userRowId(),
                info.userName(),
                info.userEmail(),
                info.role(),
                info.joinedAt()
            );
        }
    }

    public record ListResponse(
        List<Response> groups
    ) {
        public static ListResponse from(List<UserGroupServiceDto.GroupInfo> infos) {
            return new ListResponse(
                infos.stream().map(Response::from).toList()
            );
        }
    }

    public record InviteCodeResponse(
        String inviteCode
    ) {}
}
