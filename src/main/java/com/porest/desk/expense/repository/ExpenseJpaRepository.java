package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository("expenseJpaRepository")
@RequiredArgsConstructor
public class ExpenseJpaRepository implements ExpenseRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<Expense> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.rowId = :rowId AND e.isDeleted = :isDeleted", Expense.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

        if (categoryRowId != null) {
            conditions.add(" AND e.category.rowId = :categoryRowId");
        }
        if (expenseType != null) {
            conditions.add(" AND e.expenseType = :expenseType");
        }
        if (startDate != null) {
            conditions.add(" AND e.expenseDate >= :startDate");
        }
        if (endDate != null) {
            conditions.add(" AND e.expenseDate <= :endDate");
        }

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY e.expenseDate DESC, e.rowId DESC");

        TypedQuery<Expense> query = entityManager.createQuery(jpql.toString(), Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N);

        if (categoryRowId != null) {
            query.setParameter("categoryRowId", categoryRowId);
        }
        if (expenseType != null) {
            query.setParameter("expenseType", expenseType);
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }

        return query.getResultList();
    }

    @Override
    public List<Expense> findDailySummary(Long userRowId, LocalDate date) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate = :date ORDER BY e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("date", date)
            .getResultList();
    }

    @Override
    public List<Expense> findMonthlySummary(Long userRowId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate >= :startDate AND e.expenseDate <= :endDate ORDER BY e.expenseDate DESC, e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public Expense save(Expense entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(Expense entity) {
        entity.deleteExpense();
    }
}
