package com.porest.desk.savingGoal.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.savingGoal.domain.SavingGoal;
import com.porest.desk.savingGoal.repository.SavingGoalRepository;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SavingGoalServiceImpl implements SavingGoalService {
    private final SavingGoalRepository savingGoalRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public SavingGoalServiceDto.GoalInfo createSavingGoal(SavingGoalServiceDto.CreateCommand command) {
        log.debug("저축 목표 생성 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        Asset linkedAsset = resolveLinkedAsset(command.linkedAssetRowId(), command.userRowId());

        SavingGoal goal = SavingGoal.createSavingGoal(
            user,
            command.title(),
            command.description(),
            command.targetAmount(),
            command.currency(),
            command.deadlineDate(),
            command.icon(),
            command.color(),
            linkedAsset,
            command.sortOrder()
        );

        savingGoalRepository.save(goal);
        log.info("저축 목표 생성 완료: savingGoalId={}, userRowId={}", goal.getRowId(), command.userRowId());

        return SavingGoalServiceDto.GoalInfo.from(goal);
    }

    @Override
    public List<SavingGoalServiceDto.GoalInfo> getSavingGoals(Long userRowId) {
        log.debug("저축 목표 목록 조회: userRowId={}", userRowId);

        return savingGoalRepository.findByUser(userRowId).stream()
            .map(SavingGoalServiceDto.GoalInfo::from)
            .toList();
    }

    @Override
    public SavingGoalServiceDto.GoalInfo getSavingGoal(Long savingGoalId, Long userRowId) {
        log.debug("저축 목표 단건 조회: savingGoalId={}", savingGoalId);

        SavingGoal goal = findOrThrow(savingGoalId);
        validateOwnership(goal, userRowId);
        return SavingGoalServiceDto.GoalInfo.from(goal);
    }

    @Override
    @Transactional
    public SavingGoalServiceDto.GoalInfo updateSavingGoal(Long savingGoalId, Long userRowId,
                                                           SavingGoalServiceDto.UpdateCommand command) {
        log.debug("저축 목표 수정 시작: savingGoalId={}", savingGoalId);

        SavingGoal goal = findOrThrow(savingGoalId);
        validateOwnership(goal, userRowId);

        Asset linkedAsset = resolveLinkedAsset(command.linkedAssetRowId(), userRowId);

        goal.updateSavingGoal(
            command.title(),
            command.description(),
            command.targetAmount(),
            command.deadlineDate(),
            command.icon(),
            command.color(),
            linkedAsset
        );

        log.info("저축 목표 수정 완료: savingGoalId={}", savingGoalId);
        return SavingGoalServiceDto.GoalInfo.from(goal);
    }

    @Override
    @Transactional
    public SavingGoalServiceDto.GoalInfo contribute(Long savingGoalId, Long userRowId,
                                                     SavingGoalServiceDto.ContributeCommand command) {
        log.debug("저축 목표 적립 시작: savingGoalId={}, amount={}", savingGoalId, command.amount());

        SavingGoal goal = findOrThrow(savingGoalId);
        validateOwnership(goal, userRowId);

        goal.contribute(command.amount());

        log.info("저축 목표 적립 완료: savingGoalId={}, current={}, isAchieved={}",
            savingGoalId, goal.getCurrentAmount(), goal.getIsAchieved());

        return SavingGoalServiceDto.GoalInfo.from(goal);
    }

    @Override
    @Transactional
    public void deleteSavingGoal(Long savingGoalId, Long userRowId) {
        log.debug("저축 목표 삭제 시작: savingGoalId={}", savingGoalId);

        SavingGoal goal = findOrThrow(savingGoalId);
        validateOwnership(goal, userRowId);
        goal.deleteSavingGoal();

        log.info("저축 목표 삭제 완료: savingGoalId={}", savingGoalId);
    }

    @Override
    @Transactional
    public void reorderSavingGoals(Long userRowId, List<SavingGoalServiceDto.ReorderItem> items) {
        log.debug("저축 목표 정렬 변경: userRowId={}, count={}", userRowId, items.size());

        for (SavingGoalServiceDto.ReorderItem item : items) {
            SavingGoal goal = findOrThrow(item.id());
            validateOwnership(goal, userRowId);
            goal.updateSortOrder(item.sortOrder());
        }

        log.info("저축 목표 정렬 변경 완료: userRowId={}", userRowId);
    }

    private Asset resolveLinkedAsset(Long linkedAssetRowId, Long userRowId) {
        if (linkedAssetRowId == null) {
            return null;
        }
        Asset asset = assetRepository.findById(linkedAssetRowId)
            .orElseThrow(() -> {
                log.warn("연결 자산 조회 실패 - 존재하지 않는 자산: rowId={}", linkedAssetRowId);
                return new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND);
            });
        if (!asset.getUser().getRowId().equals(userRowId)) {
            log.warn("연결 자산 소유권 검증 실패 - assetId={}, ownerRowId={}, requestUserRowId={}",
                asset.getRowId(), asset.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
        return asset;
    }

    private SavingGoal findOrThrow(Long savingGoalId) {
        return savingGoalRepository.findById(savingGoalId)
            .orElseThrow(() -> {
                log.warn("저축 목표 조회 실패 - 존재하지 않는 목표: savingGoalId={}", savingGoalId);
                return new EntityNotFoundException(DeskErrorCode.SAVING_GOAL_NOT_FOUND);
            });
    }

    private void validateOwnership(SavingGoal goal, Long userRowId) {
        if (!goal.getUser().getRowId().equals(userRowId)) {
            log.warn("저축 목표 소유권 검증 실패 - savingGoalId={}, ownerRowId={}, requestUserRowId={}",
                goal.getRowId(), goal.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.SAVING_GOAL_ACCESS_DENIED);
        }
    }
}
