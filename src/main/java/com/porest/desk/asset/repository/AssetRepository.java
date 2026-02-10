package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.Asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {
    Optional<Asset> findById(Long rowId);
    List<Asset> findByUser(Long userRowId);
    Asset save(Asset asset);
    void delete(Asset asset);
}
