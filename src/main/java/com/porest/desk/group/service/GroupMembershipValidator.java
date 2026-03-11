package com.porest.desk.group.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.domain.UserGroupMember;
import com.porest.desk.group.repository.UserGroupMemberRepository;
import com.porest.desk.group.repository.UserGroupRepository;
import com.porest.desk.group.type.GroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GroupMembershipValidator {
    private final UserGroupMemberRepository memberRepo;
    private final UserGroupRepository groupRepo;

    public UserGroupMember validateMembership(Long groupRowId, Long userRowId) {
        groupRepo.findById(groupRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));
        return memberRepo.findByGroupAndUser(groupRowId, userRowId)
            .orElseThrow(() -> new ForbiddenException(DeskErrorCode.GROUP_ACCESS_DENIED));
    }

    public boolean canEditOrDelete(UserGroupMember member, Long itemOwnerRowId, Long requestUserRowId) {
        if (itemOwnerRowId.equals(requestUserRowId)) return true;
        return member.getRole() == GroupRole.OWNER || member.getRole() == GroupRole.ADMIN;
    }

    public List<Long> getUserGroupIds(Long userRowId) {
        return groupRepo.findAllByUser(userRowId).stream()
            .map(UserGroup::getRowId).toList();
    }
}
