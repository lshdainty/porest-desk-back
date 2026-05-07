package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Optional<Expense> findById(Long rowId);
    List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findByGroups(List<Long> groupRowIds, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findDailySummary(Long userRowId, LocalDate date);
    /// 임의 기간(startDate ~ endDate, inclusive) 의 사용자 거래 — 통계 집계용. fetch-join 포함.
    List<Expense> findByDateRange(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                         String keyword, String merchant, Long minAmount, Long maxAmount,
                         LocalDate startDate, LocalDate endDate);
    List<Expense> findByCalendarEvent(Long calendarEventRowId);
    List<Expense> findByTodo(Long todoRowId);
    Expense save(Expense expense);
    void delete(Expense expense);

    /**
     * 사용자의 모든 자산에 대해 월별 합계를 (asset_id, year, month, type) 그룹 단위로 한 번에 조회.
     * 반환 Object[] = { Long assetRowId, Integer year, Integer month, ExpenseType expenseType, Long amount }
     *
     * 자산 잔액 시점 재계산 전용. 자산 수/월 수에 무관하게 쿼리 1회 ─ N+1 제거용.
     * endDate 이하, is_deleted=N, asset IS NOT NULL 필터.
     */
    List<Object[]> sumMonthlyByUserGroupedByAssetAndType(Long userRowId, LocalDate endDate);

    /**
     * 임의 기간(startDate ~ endDate, inclusive) 의 (요일, 시간) 셀 단위 합계 ─ 히트맵용.
     * 반환 Object[] = { Integer mysqlDayOfWeek(1=일~7=토), Integer hour(0-23), Long totalAmount }
     * 단일 쿼리로 N+1 없음. 평균/주별 정규화는 호출자가 처리.
     */
    List<Object[]> sumGroupedByDayOfWeekAndHour(Long userRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);

    /**
     * 자산 1개 전체 이력의 주단위 (type × week) 합계 — AssetDetailDialog 차트용.
     * 반환 Object[] = { Integer yearweek (YEARWEEK mode 3, ISO), ExpenseType, Long totalAmount }
     * 단일 쿼리. scalar 초기 누적 + 기간 내 delta 를 이 1쿼리로 대체.
     * 서비스는 initial_balance 에서 시작해 주 delta 순차 누적 → window 내 주만 결과에 포함.
     */
    List<Object[]> sumAllByAssetGroupedByWeekAndType(Long assetRowId);
}
