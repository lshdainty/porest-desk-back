package com.porest.desk.user.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserApiDto {

    @Getter
    @NoArgsConstructor
    public static class ChangePasswordReq {

        @NotBlank(message = "현재 비밀번호를 입력해주세요")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호를 입력해주세요")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
        private String newPassword;

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요")
        private String confirmPassword;
    }

    public record PreferencesResponse(
        Integer budgetAlertThreshold
    ) {}

    @Getter
    @NoArgsConstructor
    public static class UpdatePreferencesReq {
        @NotNull
        @Min(value = 50, message = "예산 알림 임계값은 50% 이상이어야 합니다")
        @Max(value = 150, message = "예산 알림 임계값은 150% 이하여야 합니다")
        private Integer budgetAlertThreshold;
    }
}
