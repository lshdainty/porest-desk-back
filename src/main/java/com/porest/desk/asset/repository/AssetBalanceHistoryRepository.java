package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.type.BalanceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 특정 자산으로 <b>결제한</b> 지출의 활성 이력 row 들.
     *
     * <p>이력의 asset 이 아니라 <b>거래의 asset</b> 으로 찾는다 — 체크카드 지출은 이력이
     * 연결 계좌 앞으로 가 있어서, 이력 기준으로 찾으면 이미 옮긴 건 다시 못 찾는다.
     * 연결 계좌를 바꿀 때 A→B 로 다시 옮기려면 거래 기준이어야 한다.
     */
    @Query("""
        select h from AssetBalanceHistory h
        where h.sourceType = com.porest.desk.asset.type.BalanceSourceType.EXPENSE
          and h.isDeleted = :isDeleted
          and h.sourceRowId in (
            select e.rowId from Expense e where e.asset.rowId = :paidWithAssetRowId
          )
        """)
    List<AssetBalanceHistory> findActiveExpenseHistoryPaidWith(
        @Param("paidWithAssetRowId") Long paidWithAssetRowId,
        @Param("isDeleted") YNType isDeleted);

    /**
     * 자산별·채널별 잔액을 <b>DB 가 집계</b>해 돌려준다 — 행을 앱으로 가져오지 않는다.
     *
     * <p>잔액 = (기준시각 이하 마지막 절대 앵커) + (그 앵커 이후 flow 합) 인데, 이건
     * "그 뒤에 절대 앵커가 하나도 없는 행들의 합" 과 같다. 마지막 앵커 자신도 뒤에 앵커가
     * 없으므로 포함되고, 그 앞의 flow 는 앵커가 뒤에 있으니 빠진다.
     *
     * <p>같은 시각에 앵커가 둘이면 row_id 가 큰 쪽이 마지막이다(자바 정렬과 동일 기준).
     *
     * <p>윈도우 함수·CTE 를 쓰지 않는다 — MariaDB 버전과 H2 테스트 양쪽에서 같게 돈다.
     * 인덱스 {@code (asset_row_id, channel, effective_at)} 가 서브쿼리를 받친다.
     *
     * @return [assetRowId(Number), channel(String), balance(Number)] 행들.
     *         이력이 없는 자산은 행이 없다 — 호출 측에서 0 으로 본다.
     */
    @Query(value = """
        SELECT h.asset_row_id, h.channel, COALESCE(SUM(h.amount), 0)
        FROM asset_balance_history h
        WHERE h.asset_row_id IN (:assetIds)
          AND h.is_deleted = 'N'
          AND h.effective_at <= :at
          AND NOT EXISTS (
            SELECT 1 FROM asset_balance_history a
            WHERE a.asset_row_id = h.asset_row_id
              AND a.channel = h.channel
              AND a.is_deleted = 'N'
              AND a.effective_at <= :at
              AND a.source_type IN ('INIT', 'MANUAL', 'VALUATION')
              AND (a.effective_at > h.effective_at
                   OR (a.effective_at = h.effective_at AND a.row_id > h.row_id))
          )
        GROUP BY h.asset_row_id, h.channel
        """, nativeQuery = true)
    List<Object[]> aggregateBalances(@Param("assetIds") Collection<Long> assetIds,
                                     @Param("at") LocalDateTime at);
}
