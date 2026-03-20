package com.porest.desk.group.service;

import com.porest.desk.group.service.dto.UserGroupServiceDto;
import com.porest.desk.group.type.GroupRole;

import java.util.List;

public interface UserGroupService {
    UserGroupServiceDto.GroupInfo createGroup(UserGroupServiceDto.CreateCommand command);
    List<UserGroupServiceDto.GroupInfo> getGroups(Long userRowId);
    UserGroupServiceDto.GroupDetailInfo getGroup(Long groupRowId);
    UserGroupServiceDto.GroupInfo updateGroup(UserGroupServiceDto.UpdateCommand command);
    void deleteGroup(Long groupRowId);
    String regenerateInviteCode(Long groupRowId);
    UserGroupServiceDto.GroupDetailInfo joinByInviteCode(Long userRowId, String inviteCode);
    void removeMember(Long groupRowId, Long memberRowId);
    void changeMemberRole(Long groupRowId, Long memberRowId, GroupRole role);
}
