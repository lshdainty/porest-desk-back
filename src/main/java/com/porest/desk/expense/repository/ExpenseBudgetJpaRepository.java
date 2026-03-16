package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseBudget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("expenseBudgetJpaRepository")
@RequiredArgsConstructor
public class ExpenseBudgetJpaRepository implements ExpenseBudgetRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<ExpenseBudget> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT b FROM ExpenseBudget b WHERE b.rowId = :rowId", ExpenseBudget.class)
            .setParameter("rowId", rowId)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<ExpenseBudget> findByUser(Long userRowId, Integer year, Integer month) {
        StringBuilder jpql = new StringBuilder("SELECT b FROM ExpenseBudget b WHERE b.user.rowId = :userRowId");

        if (year != null) {
            jpql.append(" AND b.budgetYear = :year");
        }
        if (month != null) {
            jpql.append(" AND b.budgetMonth = :month");
        }
        jpql.append(" ORDER BY b.budgetYear DESC, b.budgetMonth DESC");

        TypedQuery<ExpenseBudget> query = entityManager.createQuery(jpql.toString(), ExpenseBudget.class)
            .setParameter("userRowId", userRowId);

        if (year != null) {
            query.setParameter("year", year);
        }
        if (month != null) {
            query.setParameter("month", month);
        }

        return query.getResultList();
    }

    @Override
    public List<ExpenseBudget> findAllByYearAndMonth(Integer year, Integer month) {
        return entityManager.createQuery(
            "SELECT b FROM ExpenseBudget b LEFT JOIN FETCH b.user LEFT JOIN FETCH b.category WHERE b.budgetYear = :year AND b.budgetMonth = :month", ExpenseBudget.class)
            .setParameter("year", year)
            .setParameter("month", month)
            .getResultList();
    }

    @Override
    public Optional<ExpenseBudget> findByUserAndCategory(Long userRowId, Long categoryRowId, Integer year, Integer month) {
        String jpql;
        if (categoryRowId != null) {
            jpql = "SELECT b FROM ExpenseBudget b WHERE b.user.rowId = :userRowId AND b.category.rowId = :categoryRowId AND b.budgetYear = :year AND b.budgetMonth = :month";
        } else {
            jpql = "SELECT b FROM ExpenseBudget b WHERE b.user.rowId = :userRowId AND b.category IS NULL AND b.budgetYear = :year AND b.budgetMonth = :month";
        }

        TypedQuery<ExpenseBudget> query = entityManager.createQuery(jpql, ExpenseBudget.class)
            .setParameter("userRowId", userRowId)
            .setParameter("year", year)
            .setParameter("month", month);

        if (categoryRowId != null) {
            query.setParameter("categoryRowId", categoryRowId);
        }

        return query.getResultStream().findFirst();
    }

    @Override
    public ExpenseBudget save(ExpenseBudget entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(ExpenseBudget entity) {
        entityManager.remove(entity);
    }
}
