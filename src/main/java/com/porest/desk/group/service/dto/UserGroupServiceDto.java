package com.porest.desk.group.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.domain.UserGroupMember;
import com.porest.desk.group.type.GroupRole;

import java.time.LocalDateTime;
import java.util.List;

public class UserGroupServiceDto {

    public record CreateCommand(
        Long userRowId,
        String groupName,
        String description,
        Long groupTypeId
    ) {}

    public record UpdateCommand(
        Long groupRowId,
        String groupName,
        String description,
        Long groupTypeId
    ) {}

    public record GroupInfo(
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
        public static GroupInfo from(UserGroup group) {
            long activeMembers = group.getMembers().stream()
                .filter(m -> m.getIsDeleted() == YNType.N)
                .count();
            return new GroupInfo(
                group.getRowId(),
                group.getGroupName(),
                group.getDescription(),
                group.getGroupType() != null ? group.getGroupType().getRowId() : null,
                group.getGroupType() != null ? group.getGroupType().getTypeName() : null,
                group.getGroupType() != null ? group.getGroupType().getColor() : null,
                group.getInviteCode(),
                (int) activeMembers,
                group.getCreateAt()
            );
        }
    }

    public record GroupDetailInfo(
        Long rowId,
        String groupName,
        String description,
        Long groupTypeId,
        String groupTypeName,
        String groupTypeColor,
        String inviteCode,
        List<MemberInfo> members,
        LocalDateTime createAt
    ) {
        public static GroupDetailInfo from(UserGroup group, List<UserGroupMember> members) {
            return new GroupDetailInfo(
                group.getRowId(),
                group.getGroupName(),
                group.getDescription(),
                group.getGroupType() != null ? group.getGroupType().getRowId() : null,
                group.getGroupType() != null ? group.getGroupType().getTypeName() : null,
                group.getGroupType() != null ? group.getGroupType().getColor() : null,
                group.getInviteCode(),
                members.stream().map(MemberInfo::from).toList(),
                group.getCreateAt()
            );
        }
    }

    public record MemberInfo(
        Long rowId,
        Long userRowId,
        String userName,
        String userEmail,
        GroupRole role,
        LocalDateTime joinedAt
    ) {
        public static MemberInfo from(UserGroupMember member) {
            return new MemberInfo(
                member.getRowId(),
                member.getUser().getRowId(),
                member.getUser().getUserName(),
                member.getUser().getUserEmail(),
                member.getRole(),
                member.getJoinedAt()
            );
        }
    }
}
