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

    /**
     * 한 종목의 거래를 시간순으로 — 재계산(replay)이 쓴다.
     *
     * <p>보유 id 가 붙은 거래와, 아직 id 가 없는 옛 거래(이름으로만 묶인 것)를 함께 가져온다.
     * 정렬은 (거래일시, row_id) 오름차순 — 같은 시각이면 나중에 넣은 게 뒤다.
     */
    @Query("""
        select t from AssetTrade t
        where t.asset.rowId = :assetRowId
          and t.isDeleted = com.porest.core.type.YNType.N
          and (t.holdingRowId = :holdingRowId or (t.holdingRowId is null and t.holdingKey = :holdingKey))
        order by t.tradeDate asc, t.rowId asc
        """)
    List<AssetTrade> findForReplay(@Param("assetRowId") Long assetRowId,
                                   @Param("holdingRowId") Long holdingRowId,
                                   @Param("holdingKey") String holdingKey);
}
