package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.AssetPaymentDayHistory;

import java.util.List;

public interface AssetPaymentDayHistoryRepository {
    AssetPaymentDayHistory save(AssetPaymentDayHistory history);

    /** 그 카드의 살아 있는 이력 — 효력 시작 회차 오름차순. */
    List<AssetPaymentDayHistory> findActiveByAsset(Long assetRowId);
}
