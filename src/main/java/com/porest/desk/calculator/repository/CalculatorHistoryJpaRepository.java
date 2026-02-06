package com.porest.desk.calculator.repository;

import com.porest.desk.calculator.domain.CalculatorHistory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("calculatorHistoryJpaRepository")
@RequiredArgsConstructor
public class CalculatorHistoryJpaRepository implements CalculatorHistoryRepository {
    private final EntityManager entityManager;

    @Override
    public List<CalculatorHistory> findAllByUser(Long userRowId) {
        return entityManager.createQuery(
            "SELECT h FROM CalculatorHistory h WHERE h.user.rowId = :userRowId ORDER BY h.rowId DESC", CalculatorHistory.class)
            .setParameter("userRowId", userRowId)
            .getResultList();
    }

    @Override
    public CalculatorHistory save(CalculatorHistory entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void deleteAllByUser(Long userRowId) {
        entityManager.createQuery(
            "DELETE FROM CalculatorHistory h WHERE h.user.rowId = :userRowId")
            .setParameter("userRowId", userRowId)
            .executeUpdate();
    }
}
