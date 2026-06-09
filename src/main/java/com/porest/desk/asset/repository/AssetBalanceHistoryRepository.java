package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.type.BalanceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AssetBalanceHistoryRepository extends JpaRepository<AssetBalanceHistory, Long> {

    /**
     * 주어진 자산들의 활성 이력 전체를 (자산 → effective_at → row_id) 오름차순으로 반환.
     * 호출 측에서 자산별로 grouping 하면 각 리스트가 시각 오름차순 정렬 상태가 된다.
     */
    @Query("""
        select h from AssetBalanceHistory h
        where h.asset.rowId in :assetIds and h.isDeleted = :isDeleted
        order by h.asset.rowId asc, h.effectiveAt asc, h.rowId asc
        """)
    List<AssetBalanceHistory> findActiveByAssetIds(@Param("assetIds") Collection<Long> assetIds,
                                                   @Param("isDeleted") YNType isDeleted);

    /** 특정 출처(거래/이체)로부터 생성된 활성 이력 row 들 — revert/삭제 시 soft-delete 대상. */
    @Query("""
        select h from AssetBalanceHistory h
        where h.sourceType = :sourceType and h.sourceRowId = :sourceRowId and h.isDeleted = :isDeleted
        """)
    List<AssetBalanceHistory> findActiveBySource(@Param("sourceType") BalanceSourceType sourceType,
                                                 @Param("sourceRowId") Long sourceRowId,
                                                 @Param("isDeleted") YNType isDeleted);
}
