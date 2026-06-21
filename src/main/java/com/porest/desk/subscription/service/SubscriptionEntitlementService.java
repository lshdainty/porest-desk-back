package com.porest.desk.subscription.service;

import java.util.List;

/**
 * 활성 구독에서 기능권한(entitlement)을 도출한다. 별도 권한 테이블 없이 구독이 단일 소스.
 */
public interface SubscriptionEntitlementService {

    /** 사용자가 해당 기능권한을 가진 활성 구독을 보유하는지. */
    boolean hasFeature(Long userRowId, String featureCode);

    /** 기능권한이 없으면 {@code SUBSCRIPTION_REQUIRED(403)} 를 던진다. (게이트용) */
    void requireFeature(Long userRowId, String featureCode);

    /** 사용자의 현재 활성 기능권한 코드 목록 (me/features 용). */
    List<String> getActiveFeatures(Long userRowId);
}
