package com.porest.desk.timer.repository;

import com.porest.core.type.YNType;
import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.type.TimerType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository("timerSessionJpaRepository")
@RequiredArgsConstructor
public class TimerSessionJpaRepository implements TimerSessionRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<TimerSession> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT t FROM TimerSession t WHERE t.rowId = :rowId", TimerSession.class)
            .setParameter("rowId", rowId)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<TimerSession> findByUser(Long userRowId, TimerType timerType, LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM TimerSession t WHERE t.user.rowId = :userRowId");
        List<String> conditions = new ArrayList<>();

        if (timerType != null) {
            conditions.add(" AND t.timerType = :timerType");
        }
        if (startDate != null) {
            conditions.add(" AND t.startTime >= :startDateTime");
        }
        if (endDate != null) {
            conditions.add(" AND t.startTime < :endDateTime");
        }

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY t.startTime DESC");

        TypedQuery<TimerSession> query = entityManager.createQuery(jpql.toString(), TimerSession.class)
            .setParameter("userRowId", userRowId);

        if (timerType != null) {
            query.setParameter("timerType", timerType);
        }
        if (startDate != null) {
            query.setParameter("startDateTime", startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.setParameter("endDateTime", endDate.plusDays(1).atStartOfDay());
        }

        return query.getResultList();
    }

    @Override
    public List<TimerSession> findDailyStats(Long userRowId, LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM TimerSession t WHERE t.user.rowId = :userRowId AND t.isCompleted = :isCompleted");

        if (startDate != null) {
            jpql.append(" AND t.startTime >= :startDateTime");
        }
        if (endDate != null) {
            jpql.append(" AND t.startTime < :endDateTime");
        }
        jpql.append(" ORDER BY t.startTime ASC");

        TypedQuery<TimerSession> query = entityManager.createQuery(jpql.toString(), TimerSession.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isCompleted", YNType.Y);

        if (startDate != null) {
            query.setParameter("startDateTime", startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.setParameter("endDateTime", endDate.plusDays(1).atStartOfDay());
        }

        return query.getResultList();
    }

    @Override
    public TimerSession save(TimerSession entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(TimerSession entity) {
        entityManager.remove(entity);
    }
}
