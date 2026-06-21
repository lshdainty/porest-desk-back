package com.porest.desk.subscription.service;

import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.UserSubscription;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 구독 라이프사이클. 결제(PG) 없음 — 부여는 self-grant(추후 결제완료가 {@link #subscribe} 호출),
 * 갱신/만료는 {@code processExpiry} 스케줄러가 처리.
 */
public interface SubscriptionService {

    /** 구독 부여(결제 없이 즉시 ACTIVE). 이미 활성 구독이 있으면 충돌. */
    SubscriptionInfo subscribe(Long userRowId, String planCode);

    /** 활성 구독 해지. */
    void cancel(Long userRowId, String reason);

    /** 사용자의 최근 구독 1건. */
    Optional<SubscriptionInfo> getMySubscription(Long userRowId);

    /** 만료 배치: 만료된 구독을 auto_renew 면 기간연장, 아니면 EXPIRED. 처리 건수 반환. */
    int processExpiry();

    record SubscriptionInfo(
        String planCode,
        String planName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew
    ) {
        public static SubscriptionInfo from(UserSubscription s) {
            return new SubscriptionInfo(
                s.getPlan().getPlanCode(),
                s.getPlan().getPlanName(),
                s.getStatus().name(),
                s.getStartedAt(),
                s.getCurrentPeriodEnd(),
                s.getAutoRenew() == YNType.Y
            );
        }
    }
}
