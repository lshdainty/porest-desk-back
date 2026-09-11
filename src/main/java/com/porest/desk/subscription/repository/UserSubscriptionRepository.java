package com.porest.desk.subscription.repository;

import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.UserSubscription;
import com.porest.desk.subscription.type.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * 사용자의 <b>권한이 살아 있는</b> 구독. 여러 건이면 최신 시작순.
     * plan 함께 fetch(세션 밖 plan.features 접근 대비).
     *
     * <p><b>해지해도 이미 낸 기간까지는 권한이 산다.</b> 웹·앱 해지 확인창이
     * "{날짜}부터 Free 로 전환… 그 전까지는 계속 쓸 수 있어요" 라고 약속하므로,
     * 권한 판정은 상태가 아니라 <b>남은 기간</b>을 본다 —
     * ACTIVE 이거나, CANCELLED 라도 {@code current_period_end} 가 아직 미래면 활성으로 센다.
     * 판정이 조회 시점에 이뤄지므로 <b>이미 CANCELLED 로 적힌 옛 행도 남은 기간을 그대로 되찾는다</b>
     * (데이터 손질이 필요 없다).
     *
     * <p>단 CANCELLED 쪽은 {@code current_period_end} 가 <b>있을 때만</b> 센다.
     * 만료일 없는(무제한) 구독까지 유예로 봐 주면 되돌아올 날이 없어 영구 무료가 된다 —
     * 무제한 구독의 해지는 그 자리에서 끊긴다.
     *
     * <p>기간이 지나면 이 조회에서 그대로 빠진다. 막는 일이 만료 배치에 의존하지 않는다.
     */
    @Query("""
        select s from UserSubscription s
        join fetch s.plan
        where s.userRowId = :userRowId
          and s.isDeleted = :notDeleted
          and (
                (s.status = com.porest.desk.subscription.type.SubscriptionStatus.ACTIVE
                  and (s.currentPeriodEnd is null or s.currentPeriodEnd > :now))
             or (s.status = com.porest.desk.subscription.type.SubscriptionStatus.CANCELLED
                  and s.currentPeriodEnd is not null and s.currentPeriodEnd > :now)
          )
        order by s.startedAt desc
        """)
    List<UserSubscription> findEntitled(@Param("userRowId") Long userRowId,
                                        @Param("notDeleted") YNType notDeleted,
                                        @Param("now") LocalDateTime now);

    /** 사용자의 최근 구독 1건(상태 무관) — me 조회용. plan 함께 로딩. */
    @EntityGraph(attributePaths = "plan")
    Optional<UserSubscription> findFirstByUserRowIdAndIsDeletedOrderByStartedAtDesc(Long userRowId, YNType isDeleted);

    /**
     * 만료 대상(ACTIVE 이고 만료일 경과). 스케줄러용. plan 함께 fetch(renew 시 duration 접근).
     *
     * <p>CANCELLED 는 일부러 빼 둔다 — 해지된 구독은 자동갱신이 꺼져 있어 배치가 손댈 일이 없고,
     * 기간이 지나면 {@link #findEntitled} 에서 저절로 빠져 막힌다. CANCELLED 가 그대로 남는 게
     * 맞는 종착 상태다(EXPIRED 로 덮으면 "사용자가 해지했다" 는 사실이 상태에서 지워진다).
     */
    @Query("""
        select s from UserSubscription s
        join fetch s.plan
        where s.status = :status
          and s.isDeleted = :notDeleted
          and s.currentPeriodEnd is not null
          and s.currentPeriodEnd < :now
        """)
    List<UserSubscription> findExpirable(@Param("status") SubscriptionStatus status,
                                         @Param("notDeleted") YNType notDeleted,
                                         @Param("now") LocalDateTime now);
}
