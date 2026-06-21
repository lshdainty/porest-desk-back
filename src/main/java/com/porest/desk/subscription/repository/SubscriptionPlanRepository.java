package com.porest.desk.subscription.repository;

import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByPlanCodeAndIsDeleted(String planCode, YNType isDeleted);

    List<SubscriptionPlan> findByIsActiveAndIsDeletedOrderBySortOrderAsc(YNType isActive, YNType isDeleted);
}
