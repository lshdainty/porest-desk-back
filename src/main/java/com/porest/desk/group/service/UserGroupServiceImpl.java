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
    private final UserGroupRepository userGroupRepository;
    private final UserGroupMemberRepository userGroupMemberRepository;
    private final GroupTypeRepository groupTypeRepository;
    private final UserRepository userRepository;

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

        UserGroup group = UserGroup.createGroup(command.groupName(), command.description(), groupType);
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

        group.updateGroup(command.groupName(), command.description(), groupType);
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

        UserGroupMember member = UserGroupMember.create(group, user, GroupRole.MEMBER);
        group.addMember(member);
        userGroupMemberRepository.save(member);

        log.info("그룹 참가 완료: groupId={}, userRowId={}", group.getRowId(), userRowId);

        List<UserGroupMember> members = userGroupMemberRepository.findAllByGroup(group.getRowId());
        return UserGroupServiceDto.GroupDetailInfo.from(group, members);
    }

    @Override
    @Transactional
    public void removeMember(Long groupRowId, Long memberRowId) {
        log.debug("그룹 멤버 제거: groupRowId={}, memberRowId={}", groupRowId, memberRowId);

        UserGroupMember member = userGroupMemberRepository.findById(memberRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_MEMBER_NOT_FOUND));

        member.removeMember();
        userGroupMemberRepository.save(member);
        log.info("그룹 멤버 제거 완료: memberRowId={}", memberRowId);
    }

    @Override
    @Transactional
    public void changeMemberRole(Long groupRowId, Long memberRowId, GroupRole role) {
        log.debug("그룹 멤버 역할 변경: memberRowId={}, role={}", memberRowId, role);

        UserGroupMember member = userGroupMemberRepository.findById(memberRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_MEMBER_NOT_FOUND));

        member.changeRole(role);
        userGroupMemberRepository.save(member);
        log.info("그룹 멤버 역할 변경 완료: memberRowId={}, newRole={}", memberRowId, role);
    }
}
