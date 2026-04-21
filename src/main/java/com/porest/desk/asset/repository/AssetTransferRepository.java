package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.AssetTransfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetTransferRepository {
    Optional<AssetTransfer> findById(Long rowId);
    List<AssetTransfer> findByUser(Long userRowId, LocalDate startDate, LocalDate endDate);
    AssetTransfer save(AssetTransfer transfer);
    void delete(AssetTransfer transfer);

    /**
     * 사용자의 모든 자산에 대해 이체 "들어온" 월별 합계를 (to_asset_id, year, month) 그룹 단위로 한 번에 조회.
     * 반환 Object[] = { Long toAssetRowId, Integer year, Integer month, Long amount }
     * N+1 제거용 — endDate 이하, is_deleted=N 필터.
     */
    List<Object[]> sumMonthlyTransferInByUserGroupedByAsset(Long userRowId, LocalDate endDate);

    /**
     * 사용자의 모든 자산에 대해 이체 "나간" 월별 합계를 (from_asset_id, year, month) 그룹 단위로 한 번에 조회.
     * amount + fee 합. 반환 Object[] = { Long fromAssetRowId, Integer year, Integer month, Long amount }
     */
    List<Object[]> sumMonthlyTransferOutByUserGroupedByAsset(Long userRowId, LocalDate endDate);

    /**
     * 자산 1개 전체 이력의 주단위 이체 in/out 합계 — AssetDetailDialog 차트용.
     * direction: "IN" → toAsset 기준, "OUT" → fromAsset 기준(amount+fee).
     * 반환 Object[] = { Integer yearweek (YEARWEEK mode 3), Long totalAmount }
     * 단일 쿼리. 서비스는 initial_balance 기반으로 주별 delta 누적.
     */
    List<Object[]> sumAllTransferByAssetGroupedByWeek(Long assetRowId, String direction);
}
