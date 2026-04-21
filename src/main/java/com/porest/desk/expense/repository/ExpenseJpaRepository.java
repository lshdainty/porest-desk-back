package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository("expenseJpaRepository")
@RequiredArgsConstructor
public class ExpenseJpaRepository implements ExpenseRepository {
    private final EntityManager entityManager;

    /**
     * LocalDate → LocalDateTime 변환 헬퍼
     * startDate: 해당 일자 00:00:00 (하루의 시작)
     * endDate:   해당 일자 23:59:59.999999999 (하루의 끝)
     */
    private static LocalDateTime toStartOfDay(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime toEndOfDay(LocalDate d) {
        return d.atTime(LocalTime.MAX);
    }

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
            query.setParameter("startDate", toStartOfDay(startDate));
        }
        if (endDate != null) {
            query.setParameter("endDate", toEndOfDay(endDate));
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
        if (startDate != null) query.setParameter("startDate", toStartOfDay(startDate));
        if (endDate != null) query.setParameter("endDate", toEndOfDay(endDate));

        return query.getResultList();
    }

    @Override
    public List<Expense> findDailySummary(Long userRowId, LocalDate date) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate >= :startOfDay AND e.expenseDate <= :endOfDay ORDER BY e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("startOfDay", toStartOfDay(date))
            .setParameter("endOfDay", toEndOfDay(date))
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
            .setParameter("startDate", toStartOfDay(startDate))
            .setParameter("endDate", toEndOfDay(endDate))
            .getResultList();
    }

    @Override
    public List<Expense> findWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd) {
        return entityManager.createQuery(
            "SELECT e FROM Expense e WHERE e.user.rowId = :userRowId AND e.isDeleted = :isDeleted AND e.expenseDate >= :weekStart AND e.expenseDate <= :weekEnd ORDER BY e.expenseDate DESC, e.rowId DESC", Expense.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("weekStart", toStartOfDay(weekStart))
            .setParameter("weekEnd", toEndOfDay(weekEnd))
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
            .setParameter("startDate", toStartOfDay(startDate))
            .setParameter("endDate", toEndOfDay(endDate))
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
        if (startDate != null) query.setParameter("startDate", toStartOfDay(startDate));
        if (endDate != null) query.setParameter("endDate", toEndOfDay(endDate));

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
            .setParameter("endDate", toEndOfDay(endDate))
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Object[]> sumGroupedByDayOfWeekAndHour(Long userRowId, ExpenseType expenseType, int year, int month) {
        return entityManager.createQuery(
                "SELECT FUNCTION('DAYOFWEEK', e.expenseDate), " +
                "       FUNCTION('HOUR', e.expenseDate), " +
                "       COALESCE(SUM(e.amount), 0) " +
                "FROM Expense e " +
                "WHERE e.user.rowId = :userRowId " +
                "  AND e.expenseType = :expenseType " +
                "  AND YEAR(e.expenseDate) = :year " +
                "  AND MONTH(e.expenseDate) = :month " +
                "  AND e.isDeleted = :isDeleted " +
                "GROUP BY FUNCTION('DAYOFWEEK', e.expenseDate), FUNCTION('HOUR', e.expenseDate)",
                Object[].class)
            .setParameter("userRowId", userRowId)
            .setParameter("expenseType", expenseType)
            .setParameter("year", year)
            .setParameter("month", month)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Object[]> sumByAssetGroupedByWeekAndType(Long assetRowId, LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery(
                "SELECT FUNCTION('YEARWEEK', e.expenseDate, 3), e.expenseType, COALESCE(SUM(e.amount), 0) " +
                "FROM Expense e " +
                "WHERE e.asset.rowId = :assetRowId " +
                "  AND e.expenseDate >= :startDate " +
                "  AND e.expenseDate <= :endDate " +
                "  AND e.isDeleted = :isDeleted " +
                "GROUP BY FUNCTION('YEARWEEK', e.expenseDate, 3), e.expenseType",
                Object[].class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("startDate", toStartOfDay(startDate))
            .setParameter("endDate", toEndOfDay(endDate))
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public Long sumAmountByAssetAndTypeBeforeDate(Long assetRowId, ExpenseType expenseType, LocalDate beforeDate) {
        Long sum = entityManager.createQuery(
                "SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
                "WHERE e.asset.rowId = :assetRowId " +
                "  AND e.expenseType = :expenseType " +
                "  AND e.expenseDate < :beforeDate " +
                "  AND e.isDeleted = :isDeleted", Long.class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("expenseType", expenseType)
            .setParameter("beforeDate", toStartOfDay(beforeDate))
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return sum != null ? sum : 0L;
    }
}
