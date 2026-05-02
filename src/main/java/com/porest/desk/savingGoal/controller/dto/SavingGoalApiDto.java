package com.porest.desk.savingGoal.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SavingGoalApiDto {

    public record CreateSavingGoalRequest(
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

    public record UpdateSavingGoalRequest(
        String title,
        String description,
        Long targetAmount,
        LocalDate deadlineDate,
        String icon,
        String color,
        Long linkedAssetRowId
    ) {}

    public record ContributeRequest(
        Long amount,
        String note
    ) {}

    public record ReorderRequest(List<ReorderItem> items) {}

    public record ReorderItem(Long id, Integer sortOrder) {}

    public record SavingGoalResponse(
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
        public static SavingGoalResponse from(SavingGoalServiceDto.GoalInfo info) {
            return new SavingGoalResponse(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.description(),
                info.targetAmount(),
                info.currentAmount(),
                info.currency(),
                info.deadlineDate(),
                info.icon(),
                info.color(),
                info.linkedAssetRowId(),
                info.sortOrder(),
                info.isAchieved(),
                info.achievedAt(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record SavingGoalListResponse(List<SavingGoalResponse> goals) {
        public static SavingGoalListResponse from(List<SavingGoalServiceDto.GoalInfo> infos) {
            return new SavingGoalListResponse(infos.stream().map(SavingGoalResponse::from).toList());
        }
    }
}
