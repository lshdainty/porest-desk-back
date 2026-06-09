package com.porest.desk.export.repository;

import com.porest.core.type.YNType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 내보내기 건수 전용 COUNT 쿼리 — 대용량 타입(거래·캘린더·할일)에서 전체 행을
 * 메모리에 적재하지 않고 COUNT(*) 만 수행. 기존 find* 쿼리와 동일 필터 조건을 미러.
 *
 * <p>자산·카테고리·예산·메모는 사용자당 건수가 작아 ExportDataService 에서 목록 size 로 둔다.
 */
@Repository
public class ExportCountRepository {

    @PersistenceContext
    private EntityManager em;

    /** 거래: expenseDate ∈ [start 00:00, end 23:59:59] (findByDateRange 미러). */
    public long countExpense(Long userRowId, LocalDate start, LocalDate end) {
        return em.createQuery(
                "SELECT COUNT(e) FROM Expense e " +
                "WHERE e.user.rowId = :uid AND e.isDeleted = :n " +
                "AND e.expenseDate >= :start AND e.expenseDate <= :end", Long.class)
            .setParameter("uid", userRowId)
            .setParameter("n", YNType.N)
            .setParameter("start", start.atStartOfDay())
            .setParameter("end", end.atTime(LocalTime.MAX))
            .getSingleResult();
    }

    /** 캘린더: 기간과 겹치는 이벤트 (startDate ≤ end AND endDate ≥ start — findByUserAndDateRange 미러). */
    public long countCalendar(Long userRowId, LocalDate start, LocalDate end) {
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);
        return em.createQuery(
                "SELECT COUNT(e) FROM CalendarEvent e " +
                "WHERE e.user.rowId = :uid AND e.isDeleted = :n " +
                "AND e.startDate <= :end AND e.endDate >= :start", Long.class)
            .setParameter("uid", userRowId)
            .setParameter("n", YNType.N)
            .setParameter("start", startDt)
            .setParameter("end", endDt)
            .getSingleResult();
    }

    /** 할일: dueDate ∈ [start, end] (findByUserAndDueDateBetween 미러). */
    public long countTodo(Long userRowId, LocalDate start, LocalDate end) {
        return em.createQuery(
                "SELECT COUNT(t) FROM Todo t " +
                "WHERE t.user.rowId = :uid AND t.isDeleted = :n " +
                "AND t.dueDate >= :start AND t.dueDate <= :end", Long.class)
            .setParameter("uid", userRowId)
            .setParameter("n", YNType.N)
            .setParameter("start", start)
            .setParameter("end", end)
            .getSingleResult();
    }
}
