package com.porest.desk.savingGoal.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.savingGoal.domain.SavingGoal;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SavingGoalServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String description,
        Long targetAmount,
        String currency,
        LocalDate deadlineDate,
        String icon,
        String color,
        Long linkedAssetRowId,
        Integer sortOrder
    ) {}

    /** 수정 명령 — 각 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}). */
    public record UpdateCommand(
        Patch<String> title,
        Patch<String> description,
        Patch<Long> targetAmount,
        Patch<LocalDate> deadlineDate,
        Patch<String> icon,
        Patch<String> color,
        Patch<Long> linkedAssetRowId
    ) {}

    public record ContributeCommand(
        Long amount,
        String note
    ) {}

    public record ReorderItem(
        Long id,
        Integer sortOrder
    ) {}

    public record GoalInfo(
        Long rowId,
        Long userRowId,
        String title,
        String description,
        Long targetAmount,
        Long currentAmount,
        String currency,
        LocalDate deadlineDate,
        String icon,
        String color,
        Long linkedAssetRowId,
        Integer sortOrder,
        YNType isAchieved,
        LocalDateTime achievedAt,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static GoalInfo from(SavingGoal goal) {
            return new GoalInfo(
                goal.getRowId(),
                goal.getUser().getRowId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getCurrency(),
                goal.getDeadlineDate(),
                goal.getIcon(),
                goal.getColor(),
                goal.getLinkedAsset() != null ? goal.getLinkedAsset().getRowId() : null,
                goal.getSortOrder(),
                goal.getIsAchieved(),
                goal.getAchievedAt(),
                goal.getCreateAt(),
                goal.getModifyAt()
            );
        }
    }
}
