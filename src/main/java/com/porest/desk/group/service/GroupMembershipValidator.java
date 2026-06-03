package com.porest.desk.group.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.domain.UserGroupMember;
import com.porest.desk.group.repository.UserGroupMemberRepository;
import com.porest.desk.group.repository.UserGroupRepository;
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
        return member.getRole().canWrite();
    }

    /** 그룹 콘텐츠 쓰기 권한(생성) 검증. 읽기전용(READ) 멤버는 차단. */
    public UserGroupMember validateCanWrite(Long groupRowId, Long userRowId) {
        UserGroupMember member = validateMembership(groupRowId, userRowId);
        if (!member.getRole().canWrite()) {
            throw new ForbiddenException(DeskErrorCode.GROUP_ACCESS_DENIED);
        }
        return member;
    }

    /** 그룹 관리 권한(멤버 초대·퇴출·권한변경) 검증. 소유자만 허용. */
    public UserGroupMember validateOwner(Long groupRowId, Long userRowId) {
        UserGroupMember member = validateMembership(groupRowId, userRowId);
        if (!member.getRole().canManageMembers()) {
            throw new ForbiddenException(DeskErrorCode.GROUP_ACCESS_DENIED);
        }
        return member;
    }

    public List<Long> getUserGroupIds(Long userRowId) {
        return groupRepo.findAllByUser(userRowId).stream()
            .map(UserGroup::getRowId).toList();
    }
}
