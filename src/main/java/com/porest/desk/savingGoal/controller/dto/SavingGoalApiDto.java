package com.porest.desk.savingGoal.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SavingGoalApiDto {

    /*
     * 저축 목표는 서버 검증이 통째로 없어 targetAmount −5 도 99조도 그대로 저장됐다
     * (QA 2026-09-03 #52 — 화면에 5 로 보인 건 프론트가 부호를 지운 결과다). 목표 금액은
     * 거래와 같은 100억(AmountLimits.MAX_TX_AMOUNT)으로 묶는다.
     *
     * 이름 길이·중복 안내는 여기서 다루지 않는다 — #52 의 나머지 절반이고 담당이 다르다.
     */

    public record CreateSavingGoalRequest(
        String title,
        String description,
        @Min(value = 1, message = "목표 금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "목표 금액은 100억원까지 입력할 수 있어요")
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
        @Min(value = 1, message = "목표 금액은 0보다 커야 해요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "목표 금액은 100억원까지 입력할 수 있어요")
        Long targetAmount,
        LocalDate deadlineDate,
        String icon,
        String color,
        Long linkedAssetRowId
    ) {}

    public record ContributeRequest(
        /**
         * 증감액 — 적립(+)과 <b>회수(−)</b> 양방향이다. 하한을 0 으로 두면 회수가 400 으로 죽는다
         * ({@code SavingGoal.contribute} 가 0 미만으로 안 내려가게 이미 보정한다). 크기만 본다.
         */
        @Min(value = -AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "금액은 100억원까지 입력할 수 있어요")
        Long amount,
        // note 는 어디에도 저장되지 않는다(서비스에서 버려진다) — 길이 제한을 걸 대상이 없다.
        String note
    ) {}

    @Schema(name = "SavingGoalReorderRequest")
    public record ReorderRequest(List<ReorderItem> items) {}

    @Schema(name = "SavingGoalReorderItem")
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
