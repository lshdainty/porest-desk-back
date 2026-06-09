package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.type.AssetType;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {
    Optional<Asset> findById(Long rowId);
    List<Asset> findByUser(Long userRowId);
    Asset save(Asset asset);
    void delete(Asset asset);

    /** 삭제되지 않은 특정 타입의 전체 자산 조회 (스케줄러용 — 사용자 무관). */
    List<Asset> findAllByType(AssetType assetType);
}
