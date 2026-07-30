package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository("holidayJpaRepository")
@RequiredArgsConstructor
public class HolidayJpaRepository implements HolidayRepository {
    private final EntityManager entityManager;

    @Override
    public List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery(
            "SELECT h FROM Holiday h WHERE h.isDeleted = :isDeleted AND h.holidayDate >= :startDate AND h.holidayDate <= :endDate ORDER BY h.holidayDate ASC, h.holidayName ASC", Holiday.class)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public List<Holiday> findByYearIncludingDeleted(int year) {
        return entityManager.createQuery(
            "SELECT h FROM Holiday h WHERE h.holidayDate >= :from AND h.holidayDate <= :to ORDER BY h.holidayDate ASC, h.holidayName ASC", Holiday.class)
            .setParameter("from", LocalDate.of(year, 1, 1))
            .setParameter("to", LocalDate.of(year, 12, 31))
            .getResultList();
    }

    @Override
    public Holiday save(Holiday entity) {
        entityManager.persist(entity);
        return entity;
    }
}
