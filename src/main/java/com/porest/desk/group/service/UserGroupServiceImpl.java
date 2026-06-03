package com.porest.desk.group.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.group.domain.GroupType;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.domain.UserGroupMember;
import com.porest.desk.group.repository.GroupTypeRepository;
import com.porest.desk.group.repository.UserGroupMemberRepository;
import com.porest.desk.group.repository.UserGroupRepository;
import com.porest.desk.group.service.dto.UserGroupServiceDto;
import com.porest.desk.group.type.GroupRole;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserGroupServiceImpl implements UserGroupService {
    private static final String DEFAULT_GROUP_COLOR = "#2c70bf";

    private final UserGroupRepository userGroupRepository;
    private final UserGroupMemberRepository userGroupMemberRepository;
    private final GroupTypeRepository groupTypeRepository;
    private final UserRepository userRepository;
    private final GroupMembershipValidator groupMembershipValidator;

    @Override
    @Transactional
    public UserGroupServiceDto.GroupInfo createGroup(UserGroupServiceDto.CreateCommand command) {
        log.debug("그룹 생성: userRowId={}, groupName={}", command.userRowId(), command.groupName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        GroupType groupType = null;
        if (command.groupTypeId() != null) {
            groupType = groupTypeRepository.findById(command.groupTypeId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_TYPE_NOT_FOUND));
        }

        String color = resolveGroupColor(command.color(), groupType);
        UserGroup group = UserGroup.createGroup(command.groupName(), command.description(), groupType, color);
        userGroupRepository.save(group);

        UserGroupMember ownerMember = UserGroupMember.create(group, user, GroupRole.OWNER);
        group.addMember(ownerMember);
        userGroupMemberRepository.save(ownerMember);

        log.info("그룹 생성 완료: groupId={}, inviteCode={}", group.getRowId(), group.getInviteCode());
        return UserGroupServiceDto.GroupInfo.from(group);
    }

    @Override
    public List<UserGroupServiceDto.GroupInfo> getGroups(Long userRowId) {
        log.debug("그룹 목록 조회: userRowId={}", userRowId);

        return userGroupRepository.findAllByUser(userRowId).stream()
            .map(UserGroupServiceDto.GroupInfo::from)
            .toList();
    }

    @Override
    public List<UserGroupServiceDto.SiblingMemberInfo> getSiblingMembers(Long userRowId) {
        log.debug("그룹 멤버 풀 조회: userRowId={}", userRowId);

        List<UserGroupMember> members = userGroupMemberRepository.findAllSiblingMembersOfUser(userRowId);
        Map<Long, UserGroupServiceDto.SiblingMemberInfo> byUser = new LinkedHashMap<>();

        for (UserGroupMember m : members) {
            Long uid = m.getUser().getRowId();
            if (uid.equals(userRowId)) continue;
            UserGroupServiceDto.SiblingMemberInfo existing = byUser.get(uid);
            if (existing == null) {
                List<Long> groups = new ArrayList<>();
                groups.add(m.getGroup().getRowId());
                byUser.put(uid, new UserGroupServiceDto.SiblingMemberInfo(
                    uid, m.getUser().getUserName(), m.getUser().getUserEmail(), groups
                ));
            } else {
                List<Long> merged = new ArrayList<>(existing.sharedGroupRowIds());
                merged.add(m.getGroup().getRowId());
                byUser.put(uid, new UserGroupServiceDto.SiblingMemberInfo(
                    existing.userRowId(), existing.userName(), existing.userEmail(), merged
                ));
            }
        }
        return List.copyOf(byUser.values());
    }

    @Override
    public UserGroupServiceDto.GroupDetailInfo getGroup(Long groupRowId) {
        log.debug("그룹 상세 조회: groupRowId={}", groupRowId);

        UserGroup group = userGroupRepository.findById(groupRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        List<UserGroupMember> members = userGroupMemberRepository.findAllByGroup(groupRowId);
        return UserGroupServiceDto.GroupDetailInfo.from(group, members);
    }

    @Override
    @Transactional
    public UserGroupServiceDto.GroupInfo updateGroup(UserGroupServiceDto.UpdateCommand command) {
        log.debug("그룹 수정: groupRowId={}", command.groupRowId());

        UserGroup group = userGroupRepository.findById(command.groupRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        GroupType groupType = null;
        if (command.groupTypeId() != null) {
            groupType = groupTypeRepository.findById(command.groupTypeId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_TYPE_NOT_FOUND));
        }

        String color = resolveGroupColor(command.color(), groupType);
        group.updateGroup(command.groupName(), command.description(), groupType, color);
        userGroupRepository.save(group);

        log.info("그룹 수정 완료: groupId={}", group.getRowId());
        return UserGroupServiceDto.GroupInfo.from(group);
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupRowId) {
        log.debug("그룹 삭제: groupRowId={}", groupRowId);

        UserGroup group = userGroupRepository.findById(groupRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        group.deleteGroup();
        userGroupRepository.save(group);
        log.info("그룹 삭제 완료: groupId={}", groupRowId);
    }

    @Override
    @Transactional
    public String regenerateInviteCode(Long groupRowId) {
        log.debug("초대코드 재생성: groupRowId={}", groupRowId);

        UserGroup group = userGroupRepository.findById(groupRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        String newCode = group.regenerateInviteCode();
        userGroupRepository.save(group);

        log.info("초대코드 재생성 완료: groupId={}, newCode={}", groupRowId, newCode);
        return newCode;
    }

    @Override
    @Transactional
    public UserGroupServiceDto.GroupDetailInfo joinByInviteCode(Long userRowId, String inviteCode) {
        log.debug("초대코드로 그룹 참가: userRowId={}, inviteCode={}", userRowId, inviteCode);

        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        UserGroup group = userGroupRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        // 이미 멤버인지 확인
        userGroupMemberRepository.findByGroupAndUser(group.getRowId(), userRowId)
            .ifPresent(existing -> {
                throw new IllegalStateException("이미 그룹에 참가한 사용자입니다.");
            });

        // 초대코드로 참여한 멤버는 기본 편집가능(EDIT). 소유자가 이후 읽기전용(READ)으로 강등 가능.
        UserGroupMember member = UserGroupMember.create(group, user, GroupRole.EDIT);
        group.addMember(member);
        userGroupMemberRepository.save(member);

        log.info("그룹 참가 완료: groupId={}, userRowId={}", group.getRowId(), userRowId);

        List<UserGroupMember> members = userGroupMemberRepository.findAllByGroup(group.getRowId());
        return UserGroupServiceDto.GroupDetailInfo.from(group, members);
    }

    @Override
    @Transactional
    public void removeMember(Long groupRowId, Long memberRowId, Long requestUserRowId) {
        log.debug("그룹 멤버 제거: groupRowId={}, memberRowId={}", groupRowId, memberRowId);

        // 멤버 관리(퇴출)는 소유자만 가능
        groupMembershipValidator.validateOwner(groupRowId, requestUserRowId);

        UserGroupMember member = userGroupMemberRepository.findById(memberRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (member.getRole() == GroupRole.OWNER) {
            throw new IllegalStateException("소유자는 퇴출할 수 없습니다.");
        }

        member.removeMember();
        userGroupMemberRepository.save(member);
        log.info("그룹 멤버 제거 완료: memberRowId={}", memberRowId);
    }

    @Override
    @Transactional
    public void changeMemberRole(Long groupRowId, Long memberRowId, GroupRole role, Long requestUserRowId) {
        log.debug("그룹 멤버 역할 변경: memberRowId={}, role={}", memberRowId, role);

        // 권한 변경은 소유자만 가능
        groupMembershipValidator.validateOwner(groupRowId, requestUserRowId);

        if (role == GroupRole.OWNER) {
            throw new IllegalStateException("소유자 권한은 양도할 수 없습니다.");
        }

        UserGroupMember member = userGroupMemberRepository.findById(memberRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (member.getRole() == GroupRole.OWNER) {
            throw new IllegalStateException("소유자의 권한은 변경할 수 없습니다.");
        }

        member.changeRole(role);
        userGroupMemberRepository.save(member);
        log.info("그룹 멤버 역할 변경 완료: memberRowId={}, newRole={}", memberRowId, role);
    }

    /** 그룹 색상 결정: 요청 색 → 그룹타입 색 → 기본색 순 fallback. */
    private String resolveGroupColor(String requested, GroupType groupType) {
        if (requested != null && !requested.isBlank()) return requested;
        if (groupType != null && groupType.getColor() != null) return groupType.getColor();
        return DEFAULT_GROUP_COLOR;
    }
}
