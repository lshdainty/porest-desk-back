package com.porest.desk.subscription.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.subscription.domain.SubscriptionPlan;
import com.porest.desk.subscription.domain.UserSubscription;
import com.porest.desk.subscription.repository.SubscriptionPlanRepository;
import com.porest.desk.subscription.repository.UserSubscriptionRepository;
import com.porest.desk.subscription.type.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public SubscriptionInfo subscribe(Long userRowId, String planCode) {
        LocalDateTime now = LocalDateTime.now();
        // 활성 구독 중복 방지 (앱 레벨)
        if (!subscriptionRepository.findActive(userRowId, SubscriptionStatus.ACTIVE, YNType.N, now).isEmpty()) {
            throw new InvalidValueException(DeskErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
        }
        SubscriptionPlan plan = planRepository.findByPlanCodeAndIsDeleted(planCode, YNType.N)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND));

        UserSubscription sub = UserSubscription.activate(userRowId, plan, now, true);
        subscriptionRepository.save(sub);
        log.info("구독 부여: userRowId={}, plan={}", userRowId, planCode);
        return SubscriptionInfo.from(sub);
    }

    @Override
    @Transactional
    public void cancel(Long userRowId, String reason) {
        UserSubscription sub = subscriptionRepository
            .findActive(userRowId, SubscriptionStatus.ACTIVE, YNType.N, LocalDateTime.now())
            .stream().findFirst()
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.SUBSCRIPTION_NOT_FOUND));
        sub.cancel(LocalDateTime.now(), reason);
        log.info("구독 해지: userRowId={}", userRowId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionInfo> getMySubscription(Long userRowId) {
        return subscriptionRepository
            .findFirstByUserRowIdAndIsDeletedOrderByStartedAtDesc(userRowId, YNType.N)
            .map(SubscriptionInfo::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanInfo> getActivePlans() {
        return planRepository.findByIsActiveAndIsDeletedOrderBySortOrderAsc(YNType.Y, YNType.N)
            .stream().map(PlanInfo::from).toList();
    }

    @Override
    @Transactional
    public int processExpiry() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> expirable =
            subscriptionRepository.findExpirable(SubscriptionStatus.ACTIVE, YNType.N, now);
        for (UserSubscription sub : expirable) {
            if (sub.getAutoRenew() == YNType.Y) {
                sub.renew();
            } else {
                sub.expire();
            }
        }
        if (!expirable.isEmpty()) {
            log.info("구독 만료 배치: {}건 처리", expirable.size());
        }
        return expirable.size();
    }
}
