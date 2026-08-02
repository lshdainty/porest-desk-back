package com.porest.desk.user.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.user.domain.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserApiDto {

    @Getter
    @NoArgsConstructor
    public static class ChangePasswordReq {

        @NotBlank(message = "현재 비밀번호를 입력해주세요")
        private String currentPassword;

        // 길이·문자 규칙은 SSO 가 소유한다. 여기에 복제하면 SSO 정책 변경 때 조용히 어긋난다.
        @NotBlank(message = "새 비밀번호를 입력해주세요")
        private String newPassword;

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요")
        private String confirmPassword;
    }

    @Getter
    @NoArgsConstructor
    public static class VerifyPasswordReq {

        @NotBlank(message = "비밀번호를 입력해주세요")
        private String password;
    }

    /** 알림 환경설정 응답 — boolean 항목은 true/false 로 직렬화. */
    public record PreferencesResponse(
        Boolean pushEnabled,
        Boolean notifyPayment,
        Boolean notifyBudget,
        Boolean notifyAutoRecord,
        Boolean notifyDutchPay,
        Boolean notifyCalendar,
        Boolean notifyWeeklyReport,
        Boolean notifyMonthlyReport,
        Integer budgetAlertThreshold,
        Boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        String notificationSound,
        Boolean vibrationEnabled,
        Boolean emailEnabled,
        String emailFrequency,
        /** 표시 기준 지역(IANA 타임존 ID) */
        String timezone
    ) {
        private static Boolean bool(YNType v) {
            return v == null ? null : v.toBoolean();
        }

        public static PreferencesResponse from(User u) {
            return new PreferencesResponse(
                bool(u.getPushEnabled()),
                bool(u.getNotifyPayment()),
                bool(u.getNotifyBudget()),
                bool(u.getNotifyAutoRecord()),
                bool(u.getNotifyDutchPay()),
                bool(u.getNotifyCalendar()),
                bool(u.getNotifyWeeklyReport()),
                bool(u.getNotifyMonthlyReport()),
                u.getBudgetAlertThreshold(),
                bool(u.getQuietHoursEnabled()),
                u.getQuietHoursStart(),
                u.getQuietHoursEnd(),
                u.getNotificationSound(),
                bool(u.getVibrationEnabled()),
                bool(u.getEmailEnabled()),
                u.getEmailFrequency(),
                u.getTimezone()
            );
        }
    }

    /**
     * 알림 환경설정 부분 수정 (PATCH). 모든 필드 선택적 — 보낸 항목만 반영.
     * threshold·시각·enum 은 값이 있을 때만 범위/형식 검증.
     */
    @Getter
    @NoArgsConstructor
    public static class UpdatePreferencesReq {
        private Boolean pushEnabled;
        private Boolean notifyPayment;
        private Boolean notifyBudget;
        private Boolean notifyAutoRecord;
        private Boolean notifyDutchPay;
        private Boolean notifyCalendar;
        private Boolean notifyWeeklyReport;
        private Boolean notifyMonthlyReport;

        @Min(value = 50, message = "예산 알림 임계값은 50% 이상이어야 합니다")
        @Max(value = 150, message = "예산 알림 임계값은 150% 이하여야 합니다")
        private Integer budgetAlertThreshold;

        private Boolean quietHoursEnabled;

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm 이어야 합니다")
        private String quietHoursStart;

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm 이어야 합니다")
        private String quietHoursEnd;

        @Pattern(regexp = "CHIME|DEFAULT|NONE", message = "알림음은 CHIME·DEFAULT·NONE 중 하나여야 합니다")
        private String notificationSound;

        private Boolean vibrationEnabled;
        private Boolean emailEnabled;

        @Pattern(regexp = "DAILY|WEEKLY|MONTHLY", message = "발송 주기는 DAILY·WEEKLY·MONTHLY 중 하나여야 합니다")
        private String emailFrequency;

        // 표시 기준 지역(IANA 타임존 ID). null = 무변경(부분 수정).
        // 값 형식은 ZoneId 로만 판단 가능해 서비스에서 검증한다.
        private String timezone;
    }
}
