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
    public List<Expense> findByGroups(List<Long> groupRowIds, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        if (groupRowIds.isEmpty()) return List.of();

        StringBuilder jpql = new StringBuilder("SELECT e FROM Expense e WHERE e.group.rowId IN :groupRowIds AND e.isDeleted = :isDeleted");

        if (categoryRowId != null) {
            jpql.append(" AND e.category.rowId = :categoryRowId");
        }
        if (expenseType != null) {
            jpql.append(" AND e.expenseType = :expenseType");
        }
        if (startDate != null) {
            jpql.append(" AND e.expenseDate >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" AND e.expenseDate <= :endDate");
        }
        jpql.append(" ORDER BY e.expenseDate DESC, e.rowId DESC");

        TypedQuery<Expense> query = entityManager.createQuery(jpql.toString(), Expense.class)
            .setParameter("groupRowIds", groupRowIds)
            .setParameter("isDeleted", YNType.N);

        if (categoryRowId != null) query.setParameter("categoryRowId", categoryRowId);
        if (expenseType != null) query.setParameter("expenseType", expenseType);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

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
    public List<Expense> findWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate >= :weekStart AND e.expenseDate <= :weekEnd ORDER BY e.expenseDate DESC, e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("weekStart", weekStart)
            .setParameter("weekEnd", weekEnd)
            .getResultList();
    }

    @Override
    public List<Expense> findYearlySummary(Long userRowId, Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate >= :startDate AND e.expenseDate <= :endDate ORDER BY e.expenseDate DESC, e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getResultList();
    }

    @Override
    public List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                                String keyword, String merchant, Long minAmount, Long maxAmount,
                                LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted");
        List<String> conditions = new ArrayList<>();

        if (categoryRowId != null) conditions.add(" AND e.category.rowId = :categoryRowId");
        if (assetRowId != null) conditions.add(" AND e.asset.rowId = :assetRowId");
        if (expenseType != null) conditions.add(" AND e.expenseType = :expenseType");
        if (keyword != null) conditions.add(" AND (e.description LIKE :keyword OR e.merchant LIKE :keyword)");
        if (merchant != null) conditions.add(" AND e.merchant = :merchant");
        if (minAmount != null) conditions.add(" AND e.amount >= :minAmount");
        if (maxAmount != null) conditions.add(" AND e.amount <= :maxAmount");
        if (startDate != null) conditions.add(" AND e.expenseDate >= :startDate");
        if (endDate != null) conditions.add(" AND e.expenseDate <= :endDate");

        for (String condition : conditions) {
            jpql.append(condition);
        }
        jpql.append(" ORDER BY e.expenseDate DESC, e.rowId DESC");

        TypedQuery<Expense> query = entityManager.createQuery(jpql.toString(), Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N);

        if (categoryRowId != null) query.setParameter("categoryRowId", categoryRowId);
        if (assetRowId != null) query.setParameter("assetRowId", assetRowId);
        if (expenseType != null) query.setParameter("expenseType", expenseType);
        if (keyword != null) query.setParameter("keyword", "%" + keyword + "%");
        if (merchant != null) query.setParameter("merchant", merchant);
        if (minAmount != null) query.setParameter("minAmount", minAmount);
        if (maxAmount != null) query.setParameter("maxAmount", maxAmount);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        return query.getResultList();
    }

    @Override
    public List<Expense> findByCalendarEvent(Long calendarEventRowId) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.calendarEvent.rowId = :calendarEventRowId AND e.isDeleted = :isDeleted ORDER BY e.rowId DESC", Expense.class)
            .setParameter("calendarEventRowId", calendarEventRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Expense> findByTodo(Long todoRowId) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.todo.rowId = :todoRowId AND e.isDeleted = :isDeleted ORDER BY e.rowId DESC", Expense.class)
            .setParameter("todoRowId", todoRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public Expense save(Expense entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(Expense entity) {
        entity.deleteExpense();
    }

    @Override
    public List<Object[]> sumMonthlyByUserGroupedByAssetAndType(Long userRowId, LocalDate endDate) {
        return entityManager.createQuery(
                "SELECT e.asset.rowId, " +
                "       YEAR(e.expenseDate), " +
                "       MONTH(e.expenseDate), " +
                "       e.expenseType, " +
                "       COALESCE(SUM(e.amount), 0) " +
                "FROM Expense e " +
                "WHERE e.asset.user.rowId = :userRowId " +
                "  AND e.expenseDate <= :endDate " +
                "  AND e.asset IS NOT NULL " +
                "  AND e.isDeleted = :isDeleted " +
                "GROUP BY e.asset.rowId, YEAR(e.expenseDate), MONTH(e.expenseDate), e.expenseType",
                Object[].class)
            .setParameter("userRowId", userRowId)
            .setParameter("endDate", endDate)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }
}
