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
     * <p>잔액 = (기준시각 이하 마지막 절대 앵커) + (그 앵커 이후 flow 합).
     *
     * <p>앵커는 <b>(자산, 채널)당 하나</b>다. 행마다 상관 서브쿼리로 찾으면 이력 6만 행에
     * 서브쿼리를 6만 번 돌리게 되고(요약은 두 시점이라 12만 번), 조건에 낀 OR 때문에
     * 인덱스 레인지 스캔도 깨진다 — 실제로 개발기에서 요약 한 번에 11초가 걸렸다.
     * 그래서 앵커를 파생 테이블로 <b>먼저 한 번</b> 구하고 조인해서 거른다.
     *
     * <p>같은 시각에 앵커가 둘이면 row_id 큰 쪽이 마지막이다(자바 정렬과 동일 기준).
     * 그래서 앵커 시각을 잡은 뒤 그 시각의 앵커 중 최대 row_id 를 한 번 더 집는다.
     *
     * <p>윈도우 함수·CTE 를 쓰지 않는다 — MariaDB 버전과 H2 테스트 양쪽에서 같게 돈다.
     *
     * @return [assetRowId(Number), channel(String), balance(Number)] 행들.
     *         이력이 없는 자산은 행이 없다 — 호출 측에서 0 으로 본다.
     */
    @Query(value = """
        SELECT h.asset_row_id, h.channel, COALESCE(SUM(h.amount), 0)
        FROM asset_balance_history h
        LEFT JOIN (
            SELECT k.asset_row_id, k.channel, k.anchor_at, MAX(x.row_id) AS anchor_row_id
            FROM (
                SELECT g.asset_row_id, g.channel, MAX(g.effective_at) AS anchor_at
                FROM asset_balance_history g
                WHERE g.asset_row_id IN (:assetIds)
                  AND g.is_deleted = 'N'
                  AND g.effective_at <= :at
                  AND g.source_type IN ('INIT', 'MANUAL', 'VALUATION')
                GROUP BY g.asset_row_id, g.channel
            ) k
            JOIN asset_balance_history x
              ON x.asset_row_id = k.asset_row_id
             AND x.channel = k.channel
             AND x.effective_at = k.anchor_at
             AND x.is_deleted = 'N'
             AND x.source_type IN ('INIT', 'MANUAL', 'VALUATION')
            GROUP BY k.asset_row_id, k.channel, k.anchor_at
        ) a ON a.asset_row_id = h.asset_row_id AND a.channel = h.channel
        WHERE h.asset_row_id IN (:assetIds)
          AND h.is_deleted = 'N'
          AND h.effective_at <= :at
          AND (a.anchor_at IS NULL
               OR h.effective_at > a.anchor_at
               OR (h.effective_at = a.anchor_at AND h.row_id >= a.anchor_row_id))
        GROUP BY h.asset_row_id, h.channel
        """, nativeQuery = true)
    List<Object[]> aggregateBalances(@Param("assetIds") Collection<Long> assetIds,
                                     @Param("at") LocalDateTime at);

    /**
     * 구간 내 <b>일자별</b> 변동을 DB 가 집계해 돌려준다 — 추이 그래프용.
     *
     * <p>시점마다 쿼리를 날리면 12개월 추이에 12번, 26주면 26번이다. 대신 일자별로 접어
     * 한 번에 받고 자바에서 누적한다. 돌아오는 행 수는 <b>활동이 있던 날짜 수</b>라
     * 거래 건수와 무관하다.
     *
     * <p>같은 날 안에서 앵커가 찍히면 그 앞의 flow 는 묻힌다. 날짜별 앵커도
     * (자산, 채널, 날짜)당 하나이므로 파생 테이블로 <b>먼저 한 번</b> 구해 조인한다 —
     * 행마다 상관 서브쿼리를 돌리면 이력이 쌓일수록 급격히 느려진다.
     *
     * <p>{@code anchorAmount} 는 그날 마지막 앵커(없으면 null), {@code flowAfter} 는 그
     * 앵커 이후의 flow 합(앵커가 없으면 그날 flow 전부).
     *
     * <p>자바 누적: 앵커가 있는 날은 {@code running = anchorAmount + flowAfter},
     * 없는 날은 {@code running += flowAfter}.
     *
     * @return [assetRowId(Number), channel(String), date(Date), anchorAmount(Number|null), flowAfter(Number)]
     */
    @Query(value = """
        SELECT h.asset_row_id,
               h.channel,
               CAST(h.effective_at AS DATE) AS d,
               MAX(CASE WHEN h.source_type IN ('INIT', 'MANUAL', 'VALUATION') THEN h.amount END),
               COALESCE(SUM(CASE WHEN h.source_type IN ('INIT', 'MANUAL', 'VALUATION')
                                 THEN 0 ELSE h.amount END), 0)
        FROM asset_balance_history h
        LEFT JOIN (
            SELECT k.asset_row_id, k.channel, k.d, k.anchor_at, MAX(x.row_id) AS anchor_row_id
            FROM (
                SELECT g.asset_row_id, g.channel,
                       CAST(g.effective_at AS DATE) AS d,
                       MAX(g.effective_at) AS anchor_at
                FROM asset_balance_history g
                WHERE g.asset_row_id IN (:assetIds)
                  AND g.is_deleted = 'N'
                  AND g.effective_at > :from
                  AND g.effective_at <= :to
                  AND g.source_type IN ('INIT', 'MANUAL', 'VALUATION')
                GROUP BY g.asset_row_id, g.channel, CAST(g.effective_at AS DATE)
            ) k
            JOIN asset_balance_history x
              ON x.asset_row_id = k.asset_row_id
             AND x.channel = k.channel
             AND x.effective_at = k.anchor_at
             AND x.is_deleted = 'N'
             AND x.source_type IN ('INIT', 'MANUAL', 'VALUATION')
            GROUP BY k.asset_row_id, k.channel, k.d, k.anchor_at
        ) a ON a.asset_row_id = h.asset_row_id
           AND a.channel = h.channel
           AND a.d = CAST(h.effective_at AS DATE)
        WHERE h.asset_row_id IN (:assetIds)
          AND h.is_deleted = 'N'
          AND h.effective_at > :from
          AND h.effective_at <= :to
          AND (a.anchor_at IS NULL
               OR h.effective_at > a.anchor_at
               OR (h.effective_at = a.anchor_at AND h.row_id >= a.anchor_row_id))
        GROUP BY h.asset_row_id, h.channel, CAST(h.effective_at AS DATE)
        ORDER BY h.asset_row_id, h.channel, CAST(h.effective_at AS DATE)
        """, nativeQuery = true)
    List<Object[]> aggregateDailyDeltas(@Param("assetIds") Collection<Long> assetIds,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
