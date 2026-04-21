package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.QExpense;
import com.porest.desk.expense.type.ExpenseType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class ExpenseQueryDslRepository implements ExpenseRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpense expense = QExpense.expense;

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
        return Optional.ofNullable(
            queryFactory.selectFrom(expense)
                .leftJoin(expense.category).fetchJoin()
                .leftJoin(expense.asset).fetchJoin()
                .leftJoin(expense.group).fetchJoin()
                .where(expense.rowId.eq(rowId), expense.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Expense> findByUser(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(expense.user.rowId.eq(userRowId));
        builder.and(expense.isDeleted.eq(YNType.N));

        if (categoryRowId != null) {
            builder.and(expense.category.rowId.eq(categoryRowId));
        }
        if (expenseType != null) {
            builder.and(expense.expenseType.eq(expenseType));
        }
        if (startDate != null) {
            builder.and(expense.expenseDate.goe(toStartOfDay(startDate)));
        }
        if (endDate != null) {
            builder.and(expense.expenseDate.loe(toEndOfDay(endDate)));
        }

        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .leftJoin(expense.group).fetchJoin()
            .where(builder)
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findByGroups(List<Long> groupRowIds, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        if (groupRowIds.isEmpty()) return List.of();

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(expense.group.rowId.in(groupRowIds));
        builder.and(expense.isDeleted.eq(YNType.N));

        if (categoryRowId != null) {
            builder.and(expense.category.rowId.eq(categoryRowId));
        }
        if (expenseType != null) {
            builder.and(expense.expenseType.eq(expenseType));
        }
        if (startDate != null) {
            builder.and(expense.expenseDate.goe(toStartOfDay(startDate)));
        }
        if (endDate != null) {
            builder.and(expense.expenseDate.loe(toEndOfDay(endDate)));
        }

        return queryFactory.selectFrom(expense)
            .leftJoin(expense.user).fetchJoin()
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .leftJoin(expense.group).fetchJoin()
            .where(builder)
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findDailySummary(Long userRowId, LocalDate date) {
        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.goe(toStartOfDay(date)),
                expense.expenseDate.loe(toEndOfDay(date))
            )
            .orderBy(expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findMonthlySummary(Long userRowId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.goe(toStartOfDay(startDate)),
                expense.expenseDate.loe(toEndOfDay(endDate))
            )
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd) {
        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.goe(toStartOfDay(weekStart)),
                expense.expenseDate.loe(toEndOfDay(weekEnd))
            )
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findYearlySummary(Long userRowId, Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.goe(toStartOfDay(startDate)),
                expense.expenseDate.loe(toEndOfDay(endDate))
            )
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> search(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType,
                                String keyword, String merchant, Long minAmount, Long maxAmount,
                                LocalDate startDate, LocalDate endDate) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(expense.user.rowId.eq(userRowId));
        builder.and(expense.isDeleted.eq(YNType.N));

        if (categoryRowId != null) {
            builder.and(expense.category.rowId.eq(categoryRowId));
        }
        if (assetRowId != null) {
            builder.and(expense.asset.rowId.eq(assetRowId));
        }
        if (expenseType != null) {
            builder.and(expense.expenseType.eq(expenseType));
        }
        if (keyword != null && !keyword.isBlank()) {
            builder.and(expense.description.containsIgnoreCase(keyword));
        }
        if (merchant != null && !merchant.isBlank()) {
            builder.and(expense.merchant.containsIgnoreCase(merchant));
        }
        if (minAmount != null) {
            builder.and(expense.amount.goe(minAmount));
        }
        if (maxAmount != null) {
            builder.and(expense.amount.loe(maxAmount));
        }
        if (startDate != null) {
            builder.and(expense.expenseDate.goe(toStartOfDay(startDate)));
        }
        if (endDate != null) {
            builder.and(expense.expenseDate.loe(toEndOfDay(endDate)));
        }

        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(builder)
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findByCalendarEvent(Long calendarEventRowId) {
        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.calendarEvent.rowId.eq(calendarEventRowId),
                expense.isDeleted.eq(YNType.N)
            )
            .orderBy(expense.expenseDate.desc())
            .fetch();
    }

    @Override
    public List<Expense> findByTodo(Long todoRowId) {
        return queryFactory.selectFrom(expense)
            .leftJoin(expense.category).fetchJoin()
            .leftJoin(expense.asset).fetchJoin()
            .where(
                expense.todo.rowId.eq(todoRowId),
                expense.isDeleted.eq(YNType.N)
            )
            .orderBy(expense.expenseDate.desc())
            .fetch();
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
}
