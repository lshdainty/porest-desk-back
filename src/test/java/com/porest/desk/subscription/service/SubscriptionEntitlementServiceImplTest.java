package com.porest.desk.subscription.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.subscription.domain.SubscriptionPlan;
import com.porest.desk.subscription.domain.UserSubscription;
import com.porest.desk.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 기능권한 도출 프로세스 — 권한이 살아 있는 구독의 plan.features 에서 entitlement 를 도출하고,
 * 미보유 시 게이트가 403(SUBSCRIPTION_REQUIRED)을 던지는지 검증.
 *
 * <p>"권한이 살아 있는지" 의 판정(해지해도 기간까지 유지 / 기간이 지나면 막힘)은 JPQL 에 있고
 * {@code UserSubscriptionRepositoryTest} 가 H2 에서 검증한다. 여기서는 그 조회가 돌려준 구독을
 * 서비스가 그대로 권한으로 편다는 것만 본다.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementServiceImplTest {

    @Mock private UserSubscriptionRepository subscriptionRepository;
    @InjectMocks private SubscriptionEntitlementServiceImpl sut;

    private static final long USER = 1L;

    private UserSubscription activeWith(String featuresJson, boolean hasSecurities) {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        given(plan.getDurationMonths()).willReturn(1);
        // hasFeature/getFeatures 는 테스트별로 사용 여부가 달라 lenient 처리
        org.mockito.Mockito.lenient().when(plan.hasFeature("SECURITIES")).thenReturn(hasSecurities);
        org.mockito.Mockito.lenient().when(plan.getFeatures()).thenReturn(featuresJson);
        return UserSubscription.activate(USER, plan, LocalDateTime.now(), true);
    }

    @Test
    @DisplayName("활성 구독이 SECURITIES 를 포함하면 hasFeature=true")
    void hasFeature_true() {
        UserSubscription sub = activeWith("[\"SECURITIES\"]", true);
        given(subscriptionRepository.findEntitled(eq(USER), eq(YNType.N), any()))
            .willReturn(List.of(sub));

        assertThat(sut.hasFeature(USER, "SECURITIES")).isTrue();
    }

    @Test
    @DisplayName("해지했지만 기간이 남은 구독도 권한을 준다 — 돈 낸 기간은 끝까지 쓴다")
    void hasFeature_true_cancelledWithinPeriod() {
        UserSubscription sub = activeWith("[\"SECURITIES\"]", true);
        sub.cancel(LocalDateTime.now(), "사용자 요청");
        given(subscriptionRepository.findEntitled(eq(USER), eq(YNType.N), any()))
            .willReturn(List.of(sub));

        assertThat(sut.hasFeature(USER, "SECURITIES")).isTrue();
        assertThat(sut.getActiveFeatures(USER)).containsExactly("SECURITIES");
    }

    @Test
    @DisplayName("활성 구독이 없으면 hasFeature=false")
    void hasFeature_false_noActive() {
        given(subscriptionRepository.findEntitled(eq(USER), eq(YNType.N), any()))
            .willReturn(List.of());

        assertThat(sut.hasFeature(USER, "SECURITIES")).isFalse();
    }

    @Test
    @DisplayName("userRowId 가 null 이면 권한 없음(레포 조회 안 함)")
    void hasFeature_nullUser() {
        assertThat(sut.hasFeature(null, "SECURITIES")).isFalse();
    }

    @Test
    @DisplayName("권한 미보유 시 requireFeature 는 SUBSCRIPTION_REQUIRED(403)")
    void requireFeature_throws() {
        given(subscriptionRepository.findEntitled(eq(USER), eq(YNType.N), any()))
            .willReturn(List.of());

        assertThatThrownBy(() -> sut.requireFeature(USER, "SECURITIES"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getActiveFeatures 는 plan.features JSON 을 코드 목록으로 파싱")
    void getActiveFeatures_parses() {
        UserSubscription sub = activeWith("[\"SECURITIES\"]", true);
        given(subscriptionRepository.findEntitled(eq(USER), eq(YNType.N), any()))
            .willReturn(List.of(sub));

        assertThat(sut.getActiveFeatures(USER)).containsExactly("SECURITIES");
    }
}
