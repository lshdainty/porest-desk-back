package com.porest.desk.stock.repository;

import com.porest.desk.stock.domain.StockWatchGroup;

import java.util.List;
import java.util.Optional;

public interface StockWatchGroupRepository {
    List<StockWatchGroup> findAllActiveByUser(Long userRowId);

    Optional<StockWatchGroup> findActiveByIdAndUser(Long groupRowId, Long userRowId);

    long countActiveByUser(Long userRowId);

    boolean existsActiveByUserAndName(Long userRowId, String groupName);

    StockWatchGroup save(StockWatchGroup group);
}
