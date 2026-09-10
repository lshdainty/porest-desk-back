package com.porest.desk.user.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.type.SupportedCurrency;
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

    /**
     * 금액 가리기 카드 목록 (JSON 배열 문자열).
     *
     * <p>서버는 내용을 해석하지 않는다 — 카드 키 목록은 화면이 정하는 어휘이고
     * (앱 {@code hide_amounts_cards.dart} · 웹 {@code hide-amounts-cards.ts}),
     * 서버가 그걸 알고 있으면 카드를 하나 추가할 때마다 배포가 묶인다.
     *
     * <p><b>{@code null} 과 {@code "[]"} 는 뜻이 다르다.</b> {@code null} 은 "아직 한 번도
     * 올린 적 없음", {@code "[]"} 는 "사용자가 아무것도 안 가림을 골랐음" 이다. 구분하지
     * 않으면 배포 첫 실행에 서버의 빈 값이 로컬을 덮어 가려 뒀던 금액이 통째로 드러난다.
     */
    @Column(name = "hide_cards", columnDefinition = "TEXT")
    private String hideCards;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    /**
     * 새 자산·거래를 만들 때 화면이 미리 골라 두는 통화(ISO 4217, {@link SupportedCurrency}).
     *
     * <p><b>기기가 아니라 계정에 붙는다.</b> 종전에는 웹 localStorage 의 {@code pd-currency} 에만
     * 있었고 읽는 곳이 하나도 없었다(QA #124) — 설정에서 고르면 저장된 것처럼 보이는데 폰에서도,
     * 다른 브라우저에서도 아무 일이 일어나지 않았다.
     *
     * <p>이 값은 <b>기본값일 뿐</b>이다. 자산·거래는 각자 통화를 들고 있고
     * ({@code asset.currency}) 여기 값을 바꿔도 이미 만든 것은 따라 바뀌지 않는다.
     */
    @Column(name = "default_currency", nullable = false, length = 10)
    private String defaultCurrency;

    @Column(name = "month_start_day", nullable = false)
    private Integer monthStartDay;

    /** 예산 경고·알림 임계값(%). 기본 85. 100은 강제로 초과 알림만. */
    @Column(name = "budget_alert_threshold", nullable = false)
    private Integer budgetAlertThreshold;

    // ===== 알림 설정 =====

    /**
     * 푸시 알림 마스터 토글. 기본 Y.
     *
     * <p><b>알림 행을 만들지 말지는 이 값으로 정하지 않는다.</b> 이건 "폰을 울릴까" 지
     * "앱 안에 남길까" 가 아니다 — 껐다고 알림 목록에서까지 지우면 사용자는 그 사이 무슨 일이
     * 있었는지 볼 자리를 잃고, 목록이 빈 이유도 알 수 없다. 종류별 토글({@code notify_*})은
     * 목록까지 막지만 이건 아니다.
     *
     * <p>지금은 푸시 발송 자체가 없다(FCM·기기 토큰 테이블 없음). 발송이 생기면
     * {@code quiet_hours_*} 와 함께 <b>보낼 때</b> 보는 값이다.
     */
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
        return createUser(ssoUserRowId, userId, userName, userEmail, null);
    }

    /** @param timezone SSO 가입 지역(IANA ID). null·공백이면 Asia/Seoul. */
    public static User createUser(Long ssoUserRowId, String userId, String userName, String userEmail,
                                  String timezone) {
        User user = new User();
        user.ssoUserRowId = ssoUserRowId;
        user.userId = userId;
        user.userName = userName;
        user.userEmail = userEmail;
        user.timezone = (timezone == null || timezone.isBlank()) ? "Asia/Seoul" : timezone;
        user.defaultCurrency = SupportedCurrency.DEFAULT;
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

    /** 금액 가리기 목록 교체. {@code null} 로 되돌리는 경로는 없다 — 한 번 올리면 계속 서버가 기준이다. */
    public void updateHideCards(String hideCards) {
        this.hideCards = hideCards;
    }

    public void updateTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void updateDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
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

    /**
     * 예산 알림({@code notify_budget})을 받기로 했는가 — 알림 행을 만들기 전에 본다.
     *
     * <p>{@code N} 일 때만 끈 것으로 본다. 컬럼은 {@code NOT NULL} 이지만 읽지 못한 값(null)은
     * <b>켠 것으로</b> 붙인다 — 안 껐는데 안 오는 쪽이 껐는데 오는 쪽보다 나쁘다.
     * 못 받은 알림은 사용자가 못 받았다는 사실조차 모른다.
     */
    public boolean allowsBudgetNotification() {
        return notifyBudget != YNType.N;
    }

    /**
     * 일정 알림({@code notify_calendar})을 받기로 했는가.
     * 판정 규칙은 {@link #allowsBudgetNotification()} 과 같다.
     */
    public boolean allowsCalendarNotification() {
        return notifyCalendar != YNType.N;
    }

    public void deleteUser() {
        this.isDeleted = YNType.Y;
    }
}
