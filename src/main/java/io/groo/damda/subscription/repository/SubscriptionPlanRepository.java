package io.groo.damda.subscription.repository;

import io.groo.core.type.YNType;
import io.groo.damda.subscription.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByPlanCodeAndIsDeleted(String planCode, YNType isDeleted);

    List<SubscriptionPlan> findByIsActiveAndIsDeletedOrderBySortOrderAsc(YNType isActive, YNType isDeleted);
}
