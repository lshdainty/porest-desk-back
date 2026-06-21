package com.porest.desk.subscription.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.subscription.repository.UserSubscriptionRepository;
import com.porest.desk.subscription.type.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEntitlementServiceImpl implements SubscriptionEntitlementService {

    private final UserSubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeature(Long userRowId, String featureCode) {
        if (userRowId == null) {
            return false;
        }
        return subscriptionRepository
            .findActive(userRowId, SubscriptionStatus.ACTIVE, YNType.N, LocalDateTime.now())
            .stream()
            .anyMatch(s -> s.getPlan().hasFeature(featureCode));
    }

    @Override
    public void requireFeature(Long userRowId, String featureCode) {
        if (!hasFeature(userRowId, featureCode)) {
            throw new ForbiddenException(DeskErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getActiveFeatures(Long userRowId) {
        if (userRowId == null) {
            return List.of();
        }
        return subscriptionRepository
            .findActive(userRowId, SubscriptionStatus.ACTIVE, YNType.N, LocalDateTime.now())
            .stream()
            .flatMap(s -> parseFeatures(s.getPlan().getFeatures()).stream())
            .distinct()
            .toList();
    }

    /**
     * features JSON 배열(예: {@code ["SECURITIES","A"]})을 코드 목록으로 파싱.
     * 코드는 대문자 식별자뿐이라 외부 JSON 라이브러리 없이 간단 파싱한다(ObjectMapper 빈 의존 제거).
     */
    private List<String> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String inner = json.trim();
        if (inner.startsWith("[")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("]")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        if (inner.isBlank()) {
            return List.of();
        }
        return Arrays.stream(inner.split(","))
            .map(s -> s.trim().replaceAll("^\"|\"$", ""))
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
