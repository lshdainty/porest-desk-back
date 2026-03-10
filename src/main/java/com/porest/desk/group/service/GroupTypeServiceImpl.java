package com.porest.desk.group.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.group.domain.GroupType;
import com.porest.desk.group.repository.GroupTypeRepository;
import com.porest.desk.group.service.dto.GroupTypeServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GroupTypeServiceImpl implements GroupTypeService {
    private final GroupTypeRepository groupTypeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public GroupTypeServiceDto.GroupTypeInfo createGroupType(GroupTypeServiceDto.CreateCommand command) {
        log.debug("그룹 타입 등록 시작: userRowId={}, typeName={}", command.userRowId(), command.typeName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        GroupType groupType = GroupType.createGroupType(
            user,
            command.typeName(),
            command.color(),
            command.sortOrder()
        );

        groupTypeRepository.save(groupType);
        log.info("그룹 타입 등록 완료: groupTypeId={}, userRowId={}", groupType.getRowId(), command.userRowId());

        return GroupTypeServiceDto.GroupTypeInfo.from(groupType);
    }

    @Override
    @Transactional
    public List<GroupTypeServiceDto.GroupTypeInfo> getGroupTypes(Long userRowId) {
        log.debug("그룹 타입 목록 조회: userRowId={}", userRowId);

        List<GroupType> groupTypes = groupTypeRepository.findAllByUser(userRowId);

        if (groupTypes.isEmpty()) {
            log.info("그룹 타입 기본 데이터 생성: userRowId={}", userRowId);

            User user = userRepository.findById(userRowId)
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

            groupTypes = seedDefaultGroupTypes(user);
        }

        return groupTypes.stream()
            .map(GroupTypeServiceDto.GroupTypeInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public GroupTypeServiceDto.GroupTypeInfo updateGroupType(GroupTypeServiceDto.UpdateCommand command) {
        log.debug("그룹 타입 수정 시작: groupTypeId={}", command.groupTypeId());

        GroupType groupType = findGroupTypeOrThrow(command.groupTypeId());
        validateOwnership(groupType, command.userRowId());

        groupType.updateGroupType(
            command.typeName(),
            command.color(),
            command.sortOrder()
        );

        log.info("그룹 타입 수정 완료: groupTypeId={}", command.groupTypeId());

        return GroupTypeServiceDto.GroupTypeInfo.from(groupType);
    }

    @Override
    @Transactional
    public void deleteGroupType(Long groupTypeId, Long userRowId) {
        log.debug("그룹 타입 삭제 시작: groupTypeId={}", groupTypeId);

        GroupType groupType = findGroupTypeOrThrow(groupTypeId);
        validateOwnership(groupType, userRowId);

        groupType.deleteGroupType();

        log.info("그룹 타입 삭제 완료: groupTypeId={}", groupTypeId);
    }

    private void validateOwnership(GroupType groupType, Long userRowId) {
        if (!groupType.getUser().getRowId().equals(userRowId)) {
            log.warn("그룹 타입 소유권 검증 실패 - groupTypeId={}, ownerRowId={}, requestUserRowId={}",
                groupType.getRowId(), groupType.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    private GroupType findGroupTypeOrThrow(Long groupTypeId) {
        return groupTypeRepository.findById(groupTypeId)
            .orElseThrow(() -> {
                log.warn("그룹 타입 조회 실패 - 존재하지 않는 그룹 타입: groupTypeId={}", groupTypeId);
                return new EntityNotFoundException(DeskErrorCode.GROUP_TYPE_NOT_FOUND);
            });
    }

    private List<GroupType> seedDefaultGroupTypes(User user) {
        List<GroupType> defaults = new ArrayList<>();

        defaults.add(GroupType.createGroupType(user, "가족", "#ef4444", 1));
        defaults.add(GroupType.createGroupType(user, "커플", "#ec4899", 2));
        defaults.add(GroupType.createGroupType(user, "친구", "#3b82f6", 3));
        defaults.add(GroupType.createGroupType(user, "기타", "#6b7280", 4));

        for (GroupType groupType : defaults) {
            groupTypeRepository.save(groupType);
        }

        return defaults;
    }
}
