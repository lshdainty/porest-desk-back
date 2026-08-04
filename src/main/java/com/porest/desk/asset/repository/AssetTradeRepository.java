package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AssetTradeRepository extends JpaRepository<AssetTrade, Long> {

    /** 자산의 거래 내역 (최신순). */
    @Query("""
        select t from AssetTrade t
        where t.asset.rowId = :assetRowId and t.isDeleted = :isDeleted
        order by t.tradeDate desc, t.rowId desc
        """)
    List<AssetTrade> findActiveByAsset(@Param("assetRowId") Long assetRowId,
                                       @Param("isDeleted") YNType isDeleted);

    /** 사용자의 기간 내 거래 — 실현손익 집계·목록용. */
    @Query("""
        select t from AssetTrade t
        where t.user.rowId = :userRowId and t.isDeleted = :isDeleted
          and t.tradeDate >= :start and t.tradeDate <= :end
        order by t.tradeDate desc, t.rowId desc
        """)
    List<AssetTrade> findActiveByUserAndPeriod(@Param("userRowId") Long userRowId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("isDeleted") YNType isDeleted);
}
