package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository("holidayJpaRepository")
@RequiredArgsConstructor
public class HolidayJpaRepository implements HolidayRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<Holiday> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT h FROM Holiday h WHERE h.rowId = :rowId AND h.isDeleted = :isDeleted", Holiday.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery(
            "SELECT h FROM Holiday h WHERE h.isDeleted = :isDeleted AND h.isRecurring = :isRecurring AND h.holidayDate >= :startDate AND h.holidayDate <= :endDate ORDER BY h.holidayDate ASC", Holiday.class)
            .setParameter("isDeleted", YNType.N)
            .setParameter("isRecurring", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public List<Holiday> findAllRecurring() {
        return entityManager.createQuery(
            "SELECT h FROM Holiday h WHERE h.isDeleted = :isDeleted AND h.isRecurring = :isRecurring ORDER BY h.holidayDate ASC", Holiday.class)
            .setParameter("isDeleted", YNType.N)
            .setParameter("isRecurring", YNType.Y)
            .getResultList();
    }

    @Override
    public Holiday save(Holiday entity) {
        entityManager.persist(entity);
        return entity;
    }
}
