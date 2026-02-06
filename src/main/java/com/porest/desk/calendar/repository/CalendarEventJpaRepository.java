package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository("calendarEventJpaRepository")
@RequiredArgsConstructor
public class CalendarEventJpaRepository implements CalendarEventRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<CalendarEvent> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT e FROM CalendarEvent e WHERE e.rowId = :rowId AND e.isDeleted = :isDeleted", CalendarEvent.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<CalendarEvent> findByUserAndDateRange(Long userRowId, LocalDateTime startDate, LocalDateTime endDate) {
        return entityManager.createQuery(
            "SELECT e FROM CalendarEvent e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.startDate <= :endDate AND e.endDate >= :startDate ORDER BY e.startDate ASC", CalendarEvent.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public CalendarEvent save(CalendarEvent entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
