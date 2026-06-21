package com.porest.desk.subscription.type;

/** 구독 상태. */
public enum SubscriptionStatus {
    /** 활성 — 기능권한 부여. */
    ACTIVE,
    /** 만료 — 기간 종료(자동갱신 아님). */
    EXPIRED,
    /** 해지 — 사용자가 취소. */
    CANCELLED
}
