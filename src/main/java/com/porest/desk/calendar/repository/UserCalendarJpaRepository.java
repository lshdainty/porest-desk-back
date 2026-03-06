package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.UserCalendar;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("userCalendarJpaRepository")
@RequiredArgsConstructor
public class UserCalendarJpaRepository implements UserCalendarRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<UserCalendar> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT c FROM UserCalendar c WHERE c.rowId = :rowId AND c.isDeleted = :isDeleted", UserCalendar.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<UserCalendar> findAllByUser(Long userRowId) {
        return entityManager.createQuery(
            "SELECT c FROM UserCalendar c WHERE c.user.rowId = :userRowId AND c.isDeleted = :isDeleted ORDER BY c.sortOrder ASC, c.rowId ASC", UserCalendar.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public Optional<UserCalendar> findDefaultByUser(Long userRowId) {
        return entityManager.createQuery(
            "SELECT c FROM UserCalendar c WHERE c.user.rowId = :userRowId AND c.isDefault = :isDefault AND c.isDeleted = :isDeleted", UserCalendar.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDefault", YNType.Y)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public UserCalendar save(UserCalendar entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
