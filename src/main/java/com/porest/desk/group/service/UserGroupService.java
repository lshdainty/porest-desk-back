package com.porest.desk.group.service;

import com.porest.desk.group.service.dto.UserGroupServiceDto;
import com.porest.desk.group.type.GroupRole;

import java.util.List;

public interface UserGroupService {
    UserGroupServiceDto.GroupInfo createGroup(UserGroupServiceDto.CreateCommand command);
    List<UserGroupServiceDto.GroupInfo> getGroups(Long userRowId);
    /**
     * 사용자가 속한 모든 그룹의 멤버 통합 풀(자기 자신 제외, userRowId 기준 중복 제거).
     * DutchPay 참가자 자동완성 등에서 사용.
     */
    List<UserGroupServiceDto.SiblingMemberInfo> getSiblingMembers(Long userRowId);
    UserGroupServiceDto.GroupDetailInfo getGroup(Long groupRowId);
    UserGroupServiceDto.GroupInfo updateGroup(UserGroupServiceDto.UpdateCommand command);
    void deleteGroup(Long groupRowId);
    String regenerateInviteCode(Long groupRowId);
    UserGroupServiceDto.GroupDetailInfo joinByInviteCode(Long userRowId, String inviteCode);
    void removeMember(Long groupRowId, Long memberRowId);
    void changeMemberRole(Long groupRowId, Long memberRowId, GroupRole role);
}
