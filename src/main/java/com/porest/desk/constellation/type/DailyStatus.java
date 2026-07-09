package com.porest.desk.constellation.type;

/**
 * 일일 관측 상태.
 * GROWN 수집(목표 도달, 불변) · WITHERED 흐린 밤(별빛은 있었으나 미달) · REST 쉼(활동 없음 — 보호 가림용 행에만 저장, 평소엔 무행=REST).
 */
public enum DailyStatus {
    GROWN,
    WITHERED,
    REST
}
