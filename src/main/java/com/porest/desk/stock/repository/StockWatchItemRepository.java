package com.porest.desk.stock.repository;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.domain.StockWatchItem;

import java.util.List;
import java.util.Optional;

public interface StockWatchItemRepository {

    /** 관심 종목 + 종목 마스터 조인 한 건 */
    record ItemWithStock(StockWatchItem item, StockMaster stock) {
    }

    /** 사용자의 활성 그룹 전체의 활성 종목을 마스터와 조인해 조회 (그룹 정렬 → 종목 정렬 순) */
    List<ItemWithStock> findAllActiveByUserWithStock(Long userRowId);

    /**
     * (group, stock_master) 행을 삭제분까지 포함해 조회한다.
     *
     * <p>재추가 대조용. 삭제 행을 빼고 보면 유니크 제약에 걸려 재적재가 실패한다.
     */
    Optional<StockWatchItem> findByGroupAndStockIncludingDeleted(Long groupRowId, Long stockMasterRowId);

    Optional<StockWatchItem> findActiveById(Long itemRowId);

    List<StockWatchItem> findAllActiveByGroup(Long groupRowId);

    long countActiveByGroup(Long groupRowId);

    StockWatchItem save(StockWatchItem item);
}
