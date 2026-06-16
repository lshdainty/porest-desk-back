package com.porest.desk.user.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "sso_user_row_id")
    private Long ssoUserRowId;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "user_name", nullable = false, length = 20)
    private String userName;

    @Column(name = "user_email", nullable = false, length = 100)
    private String userEmail;

    @Column(name = "dashboard", columnDefinition = "TEXT")
    private String dashboard;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "month_start_day", nullable = false)
    private Integer monthStartDay;

    /** 예산 경고·알림 임계값(%). 기본 85. 100은 강제로 초과 알림만. */
    @Column(name = "budget_alert_threshold", nullable = false)
    private Integer budgetAlertThreshold;

    // ===== 알림 설정 =====

    /** 푸시 알림 마스터 토글. N이면 모든 알림 종류 비활성. 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "push_enabled", nullable = false, length = 1)
    private YNType pushEnabled;

    /** 결제 알림 (결제 예정일 D-1·당일). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_payment", nullable = false, length = 1)
    private YNType notifyPayment;

    /** 예산 알림 (카테고리 예산 도달). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_budget", nullable = false, length = 1)
    private YNType notifyBudget;

    /** 자동 기록 알림 (반복 거래 자동 기록). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_auto_record", nullable = false, length = 1)
    private YNType notifyAutoRecord;

    /** 더치페이 알림 (송금 요청·정산 완료). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_dutch_pay", nullable = false, length = 1)
    private YNType notifyDutchPay;

    /** 일정 알림 (캘린더 이벤트 시작 전). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_calendar", nullable = false, length = 1)
    private YNType notifyCalendar;

    /** 주간 리포트 (매주 월요일 오전 9시). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_weekly_report", nullable = false, length = 1)
    private YNType notifyWeeklyReport;

    /** 월간 리포트 (매월 1일 오전 9시). 기본 N. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notify_monthly_report", nullable = false, length = 1)
    private YNType notifyMonthlyReport;

    /** 방해 금지 시간 사용 여부. 기본 N. */
    @Enumerated(EnumType.STRING)
    @Column(name = "quiet_hours_enabled", nullable = false, length = 1)
    private YNType quietHoursEnabled;

    /** 방해 금지 시작 시각 ("HH:mm", 24h). 기본 22:00. */
    @Column(name = "quiet_hours_start", nullable = false, length = 5)
    private String quietHoursStart;

    /** 방해 금지 종료 시각 ("HH:mm", 24h). 기본 07:00. */
    @Column(name = "quiet_hours_end", nullable = false, length = 5)
    private String quietHoursEnd;

    /** 알림음 (CHIME·DEFAULT·NONE). 기본 CHIME. */
    @Column(name = "notification_sound", nullable = false, length = 16)
    private String notificationSound;

    /** 진동 사용 여부 (모바일). 기본 Y. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vibration_enabled", nullable = false, length = 1)
    private YNType vibrationEnabled;

    /** 이메일 알림 수신 여부. 기본 N. */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_enabled", nullable = false, length = 1)
    private YNType emailEnabled;

    /** 이메일 발송 주기 (DAILY·WEEKLY·MONTHLY). 기본 WEEKLY. */
    @Column(name = "email_frequency", nullable = false, length = 16)
    private String emailFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static User createUser(Long ssoUserRowId, String userId, String userName, String userEmail) {
        User user = new User();
        user.ssoUserRowId = ssoUserRowId;
        user.userId = userId;
        user.userName = userName;
        user.userEmail = userEmail;
        user.timezone = "Asia/Seoul";
        user.monthStartDay = 1;
        user.budgetAlertThreshold = 85;
        user.pushEnabled = YNType.Y;
        user.notifyPayment = YNType.Y;
        user.notifyBudget = YNType.Y;
        user.notifyAutoRecord = YNType.Y;
        user.notifyDutchPay = YNType.Y;
        user.notifyCalendar = YNType.Y;
        user.notifyWeeklyReport = YNType.Y;
        user.notifyMonthlyReport = YNType.N;
        user.quietHoursEnabled = YNType.N;
        user.quietHoursStart = "22:00";
        user.quietHoursEnd = "07:00";
        user.notificationSound = "CHIME";
        user.vibrationEnabled = YNType.Y;
        user.emailEnabled = YNType.N;
        user.emailFrequency = "WEEKLY";
        user.isDeleted = YNType.N;
        return user;
    }

    public void updateFromSso(Long ssoUserRowId, String userName, String userEmail) {
        this.ssoUserRowId = ssoUserRowId;
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public void updateDashboard(String dashboard) {
        this.dashboard = dashboard;
    }

    public void updateTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void updateMonthStartDay(Integer monthStartDay) {
        this.monthStartDay = monthStartDay;
    }

    public void updateBudgetAlertThreshold(Integer threshold) {
        if (threshold == null) return;
        int clamped = Math.max(50, Math.min(150, threshold));
        this.budgetAlertThreshold = clamped;
    }

    /**
     * 알림 환경설정 부분 수정 (PATCH). null 인 항목은 변경하지 않는다.
     * boolean 항목은 {@link YNType} 로 저장, 시각·enum 은 문자열 그대로.
     */
    public void updateNotificationPreferences(
            Boolean pushEnabled,
            Boolean notifyPayment,
            Boolean notifyBudget,
            Boolean notifyAutoRecord,
            Boolean notifyDutchPay,
            Boolean notifyCalendar,
            Boolean notifyWeeklyReport,
            Boolean notifyMonthlyReport,
            Boolean quietHoursEnabled,
            String quietHoursStart,
            String quietHoursEnd,
            String notificationSound,
            Boolean vibrationEnabled,
            Boolean emailEnabled,
            String emailFrequency) {
        if (pushEnabled != null) this.pushEnabled = YNType.from(pushEnabled);
        if (notifyPayment != null) this.notifyPayment = YNType.from(notifyPayment);
        if (notifyBudget != null) this.notifyBudget = YNType.from(notifyBudget);
        if (notifyAutoRecord != null) this.notifyAutoRecord = YNType.from(notifyAutoRecord);
        if (notifyDutchPay != null) this.notifyDutchPay = YNType.from(notifyDutchPay);
        if (notifyCalendar != null) this.notifyCalendar = YNType.from(notifyCalendar);
        if (notifyWeeklyReport != null) this.notifyWeeklyReport = YNType.from(notifyWeeklyReport);
        if (notifyMonthlyReport != null) this.notifyMonthlyReport = YNType.from(notifyMonthlyReport);
        if (quietHoursEnabled != null) this.quietHoursEnabled = YNType.from(quietHoursEnabled);
        if (quietHoursStart != null) this.quietHoursStart = quietHoursStart;
        if (quietHoursEnd != null) this.quietHoursEnd = quietHoursEnd;
        if (notificationSound != null) this.notificationSound = notificationSound;
        if (vibrationEnabled != null) this.vibrationEnabled = YNType.from(vibrationEnabled);
        if (emailEnabled != null) this.emailEnabled = YNType.from(emailEnabled);
        if (emailFrequency != null) this.emailFrequency = emailFrequency;
    }

    public void deleteUser() {
        this.isDeleted = YNType.Y;
    }
}
