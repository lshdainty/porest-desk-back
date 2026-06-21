package com.porest.desk.subscription.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEntitlementServiceImpl implements SubscriptionEntitlementService {

    private static final TypeReference<List<String>> FEATURES_TYPE = new TypeReference<>() {};

    private final UserSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

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

    private List<String> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FEATURES_TYPE);
        } catch (Exception e) {
            log.warn("구독 플랜 features 파싱 실패: {}", json, e);
            return List.of();
        }
    }
}
