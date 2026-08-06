package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Optional<Expense> findById(Long rowId);
    List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
    List<Expense> findDailySummary(Long userRowId, LocalDate date);
    /// 임의 기간(startDate ~ endDate, inclusive) 의 사용자 거래 — 통계 집계용. fetch-join 포함.
    List<Expense> findByDateRange(Long userRowId, LocalDate startDate, LocalDate endDate);

    /** 자산으로 좁힌 기간 조회 — assetRowId 가 null 이면 전체. */
    List<Expense> findByDateRange(Long userRowId, LocalDate startDate, LocalDate endDate, Long assetRowId);
    List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                         String keyword, String merchant, Long minAmount, Long maxAmount,
                         LocalDate startDate, LocalDate endDate);
    /** 해당 카테고리를 참조하는 (삭제되지 않은) 거래 존재 여부 — 상위 승격 가드용. */
    boolean existsByCategory(Long categoryRowId);

    /** 해당 카테고리에 직접 달린 활성 거래 — 일괄 카테고리 이동용. */
    List<Expense> findActiveByCategory(Long categoryRowId);
    /** 카테고리 + 그 하위 카테고리들의 기간 내 지출 합계 (예산 알림 roll-up용). */
    long sumAmountByCategoryRollup(Long userRowId, Long categoryRowId, ExpenseType expenseType,
                                   LocalDate startDate, LocalDate endDate);
    /** 이 거래를 원거래로 삼는 활성 환불들 — 원거래를 지울 때 함께 지우려고 찾는다. */
    List<Expense> findActiveRefundsOf(Long expenseRowId);

    /** 여러 원거래의 환불을 한 번에 — 목록에서 건별로 조회하면 N+1 이 된다. */
    List<Expense> findActiveRefundsOfMany(List<Long> expenseRowIds);

    List<Expense> findByCalendarEvent(Long calendarEventRowId);
    List<Expense> findByTodo(Long todoRowId);
    Expense save(Expense expense);
    void delete(Expense expense);

    /**
     * 임의 기간(startDate ~ endDate, inclusive) 의 (요일, 시간) 셀 단위 합계 ─ 히트맵용.
     * 반환 Object[] = { Integer mysqlDayOfWeek(1=일~7=토), Integer hour(0-23), Long totalAmount }
     * 단일 쿼리로 N+1 없음. 평균/주별 정규화는 호출자가 처리.
     */
    List<Object[]> sumGroupedByDayOfWeekAndHour(Long userRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate);
}
