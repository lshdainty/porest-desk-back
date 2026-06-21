package com.porest.desk.subscription.repository;

import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.UserSubscription;
import com.porest.desk.subscription.type.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /** 사용자의 활성(미만료) 구독. 여러 건이면 최신 시작순. */
    @Query("""
        select s from UserSubscription s
        where s.userRowId = :userRowId
          and s.status = :status
          and s.isDeleted = :notDeleted
          and (s.currentPeriodEnd is null or s.currentPeriodEnd > :now)
        order by s.startedAt desc
        """)
    List<UserSubscription> findActive(@Param("userRowId") Long userRowId,
                                      @Param("status") SubscriptionStatus status,
                                      @Param("notDeleted") YNType notDeleted,
                                      @Param("now") LocalDateTime now);

    /** 사용자의 최근 구독 1건(상태 무관) — me 조회용. */
    Optional<UserSubscription> findFirstByUserRowIdAndIsDeletedOrderByStartedAtDesc(Long userRowId, YNType isDeleted);

    /** 만료 대상(ACTIVE 이고 만료일 경과). 스케줄러용. */
    @Query("""
        select s from UserSubscription s
        where s.status = :status
          and s.isDeleted = :notDeleted
          and s.currentPeriodEnd is not null
          and s.currentPeriodEnd < :now
        """)
    List<UserSubscription> findExpirable(@Param("status") SubscriptionStatus status,
                                         @Param("notDeleted") YNType notDeleted,
                                         @Param("now") LocalDateTime now);
}
