package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.AssetHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssetHoldingRepository extends JpaRepository<AssetHolding, Long> {

    /** 자산의 활성 보유 — 정렬순. */
    @Query("""
        select h from AssetHolding h
        where h.asset.rowId = :assetRowId
          and h.isDeleted = com.porest.core.type.YNType.N
        order by h.sortOrder asc, h.rowId asc
        """)
    List<AssetHolding> findActiveByAsset(@Param("assetRowId") Long assetRowId);

    /** 여러 자산의 활성 보유를 in-query 1회로 일괄 조회 (목록 N+1 방지). */
    @Query("""
        select h from AssetHolding h
        where h.asset.rowId in :assetRowIds
          and h.isDeleted = com.porest.core.type.YNType.N
        order by h.sortOrder asc, h.rowId asc
        """)
    List<AssetHolding> findActiveByAssets(@Param("assetRowIds") List<Long> assetRowIds);
}
