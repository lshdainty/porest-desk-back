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
    List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                         String keyword, String merchant, Long minAmount, Long maxAmount,
                         LocalDate startDate, LocalDate endDate);
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
