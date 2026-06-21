package com.porest.desk.subscription.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.SubscriptionPlan;
import com.porest.desk.subscription.domain.UserSubscription;
import com.porest.desk.subscription.repository.SubscriptionPlanRepository;
import com.porest.desk.subscription.repository.UserSubscriptionRepository;
import com.porest.desk.subscription.type.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 구독 라이프사이클 프로세스 — 부여(중복방지)·해지·만료/갱신.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @InjectMocks private SubscriptionServiceImpl sut;

    private static final long USER = 1L;

    private SubscriptionPlan plan(int durationMonths) {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        given(plan.getDurationMonths()).willReturn(durationMonths);
        org.mockito.Mockito.lenient().when(plan.getPlanCode()).thenReturn("SECURITIES");
        org.mockito.Mockito.lenient().when(plan.getPlanName()).thenReturn("증권 구독");
        return plan;
    }

    @Test
    @DisplayName("활성 구독이 없으면 구독 부여 시 ACTIVE 로 저장")
    void subscribe_creates_active() {
        SubscriptionPlan p = plan(1);
        given(subscriptionRepository.findActive(eq(USER), eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of());
        given(planRepository.findByPlanCodeAndIsDeleted("SECURITIES", YNType.N))
            .willReturn(Optional.of(p));

        SubscriptionService.SubscriptionInfo info = sut.subscribe(USER, "SECURITIES");

        assertThat(info.status()).isEqualTo("ACTIVE");
        verify(subscriptionRepository).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("이미 활성 구독이 있으면 SUBSCRIPTION_ALREADY_ACTIVE")
    void subscribe_alreadyActive() {
        UserSubscription existing = UserSubscription.activate(USER, plan(1), LocalDateTime.now(), true);
        given(subscriptionRepository.findActive(eq(USER), eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of(existing));

        assertThatThrownBy(() -> sut.subscribe(USER, "SECURITIES"))
            .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("존재하지 않는 플랜이면 SUBSCRIPTION_PLAN_NOT_FOUND")
    void subscribe_planNotFound() {
        given(subscriptionRepository.findActive(eq(USER), eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of());
        given(planRepository.findByPlanCodeAndIsDeleted("UNKNOWN", YNType.N)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.subscribe(USER, "UNKNOWN"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("활성 구독 해지 시 상태가 CANCELLED 로 전이")
    void cancel_setsCancelled() {
        UserSubscription sub = UserSubscription.activate(USER, plan(1), LocalDateTime.now(), true);
        given(subscriptionRepository.findActive(eq(USER), eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of(sub));

        sut.cancel(USER, "사용자 요청");

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sub.getAutoRenew()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("활성 구독이 없으면 해지 시 SUBSCRIPTION_NOT_FOUND")
    void cancel_notFound() {
        given(subscriptionRepository.findActive(eq(USER), eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of());

        assertThatThrownBy(() -> sut.cancel(USER, null))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("활성 플랜 목록을 정렬순으로 반환")
    void getActivePlans() {
        SubscriptionPlan p = plan(1);
        org.mockito.Mockito.lenient().when(p.getPlanCode()).thenReturn("SECURITIES");
        org.mockito.Mockito.lenient().when(p.getPlanName()).thenReturn("증권 구독");
        given(planRepository.findByIsActiveAndIsDeletedOrderBySortOrderAsc(YNType.Y, YNType.N))
            .willReturn(List.of(p));

        List<SubscriptionService.PlanInfo> plans = sut.getActivePlans();

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).planCode()).isEqualTo("SECURITIES");
    }

    @Test
    @DisplayName("만료 배치: auto_renew=Y 는 기간 연장, N 은 EXPIRED")
    void processExpiry_renewVsExpire() {
        UserSubscription renewable = UserSubscription.activate(USER, plan(1), LocalDateTime.now(), true);
        UserSubscription expiring = UserSubscription.activate(2L, plan(1), LocalDateTime.now(), false);
        LocalDateTime beforeRenew = renewable.getCurrentPeriodEnd();
        given(subscriptionRepository.findExpirable(eq(SubscriptionStatus.ACTIVE), eq(YNType.N), any()))
            .willReturn(List.of(renewable, expiring));

        int processed = sut.processExpiry();

        assertThat(processed).isEqualTo(2);
        assertThat(renewable.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(renewable.getCurrentPeriodEnd()).isAfter(beforeRenew); // 기간 연장
        assertThat(expiring.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }
}
