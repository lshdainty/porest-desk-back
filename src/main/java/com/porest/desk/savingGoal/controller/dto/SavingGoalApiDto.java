package com.porest.desk.savingGoal.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.AmountLimits;
import com.porest.desk.common.validation.ColorFormat;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color,
        Long linkedAssetRowId,
        Integer sortOrder
    ) {}

    /**
     * 수정 본문 — <b>실린 칸만 바꾼다</b>(QA #96, 사용자 결정 2026-09-07).
     *
     * <p>{@code Optional} 참조가 {@code null} 이면 키가 없었던 것(유지),
     * {@code Optional.empty()} 면 {@code null} 이 실린 것(지움)이다
     * ({@code AbsentAwareOptionalModule}).
     *
     * <p>{@code targetAmount} 는 NOT NULL 이라 "지운다" 가 성립하지 않는다 —
     * 명시적 {@code null} 은 {@code @NotNull} 로 400 이다. 안 보내면 지금 값을 지킨다.
     * {@code title} 도 NOT NULL 이고, 빈 이름 거절은 서비스의 이름 정규화가 이미 한다.
     */
    public record UpdateSavingGoalRequest(
        Optional<String> title,
        Optional<String> description,
        Optional<@NotNull(message = "목표 금액을 입력해 주세요")
                 @Min(value = 1, message = "목표 금액은 0보다 커야 해요")
                 @Max(value = AmountLimits.MAX_TX_AMOUNT, message = "목표 금액은 100억원까지 입력할 수 있어요")
                 Long> targetAmount,
        Optional<LocalDate> deadlineDate,
        Optional<String> icon,
        Optional<@Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
                 String> color,
        Optional<Long> linkedAssetRowId
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
