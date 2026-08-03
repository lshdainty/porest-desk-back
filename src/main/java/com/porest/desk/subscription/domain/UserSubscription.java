package com.porest.desk.subscription.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.subscription.type.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 구독. 활성 구독({@code status=ACTIVE} 이고 만료 전)에서 기능권한을 도출한다.
 * 결제(PG) 없음 — 부여/갱신은 수동·관리자·만료 스케줄러가 처리.
 */
@Entity
@Table(name = "user_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSubscription extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_row_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 현재 구독 만료 일시. null = 무제한. */
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_renew", nullable = false, length = 1)
    private YNType autoRenew;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    private UserSubscription(Long userRowId, SubscriptionPlan plan, LocalDateTime startedAt,
                            LocalDateTime currentPeriodEnd, YNType autoRenew) {
        this.userRowId = userRowId;
        this.plan = plan;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.currentPeriodEnd = currentPeriodEnd;
        this.autoRenew = autoRenew;
        this.isDeleted = YNType.N;
    }

    /**
     * 신규 활성 구독 생성. durationMonths=0(무제한)이면 만료 없음.
     */
    public static UserSubscription activate(Long userRowId, SubscriptionPlan plan, LocalDateTime now, boolean autoRenew) {
        LocalDateTime end = plan.getDurationMonths() != null && plan.getDurationMonths() > 0
            ? now.plusMonths(plan.getDurationMonths())
            : null;
        return new UserSubscription(userRowId, plan, now, end, autoRenew ? YNType.Y : YNType.N);
    }

    /** 만료 전·활성 여부. */
    public boolean isActiveAt(LocalDateTime now) {
        return status == SubscriptionStatus.ACTIVE
            && (currentPeriodEnd == null || now.isBefore(currentPeriodEnd));
    }

    /** 해지 — 자동갱신 중지, 상태 CANCELLED. */
    public void cancel(LocalDateTime now, String reason) {
        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = YNType.N;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    /** 만료 처리 — 상태 EXPIRED. */
    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
    }

    /** 갱신 — 만료일을 플랜 기간만큼 연장(결제 없이). 무제한 플랜이면 무동작. */
    public void renew() {
        if (plan.getDurationMonths() != null && plan.getDurationMonths() > 0 && currentPeriodEnd != null) {
            this.currentPeriodEnd = this.currentPeriodEnd.plusMonths(plan.getDurationMonths());
        }
    }
}
