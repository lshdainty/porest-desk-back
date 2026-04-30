package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.domain.QExpenseSplit;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class ExpenseSplitQueryDslRepository implements ExpenseSplitRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpenseSplit split = QExpenseSplit.expenseSplit;

    @Override
    public Optional<ExpenseSplit> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(split)
                .where(split.rowId.eq(rowId), split.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<ExpenseSplit> findByExpense(Long expenseRowId) {
        return queryFactory.selectFrom(split)
            .where(
                split.expense.rowId.eq(expenseRowId),
                split.isDeleted.eq(YNType.N)
            )
            .orderBy(split.sortOrder.asc(), split.rowId.asc())
            .fetch();
    }

    @Override
    public List<ExpenseSplit> findByExpenseIds(List<Long> expenseRowIds) {
        if (expenseRowIds == null || expenseRowIds.isEmpty()) return List.of();
        return queryFactory.selectFrom(split)
            .where(
                split.expense.rowId.in(expenseRowIds),
                split.isDeleted.eq(YNType.N)
            )
            .orderBy(split.expense.rowId.asc(), split.sortOrder.asc(), split.rowId.asc())
            .fetch();
    }

    @Override
    public ExpenseSplit save(ExpenseSplit entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void deleteByExpense(Long expenseRowId) {
        queryFactory.update(split)
            .set(split.isDeleted, YNType.Y)
            .where(split.expense.rowId.eq(expenseRowId), split.isDeleted.eq(YNType.N))
            .execute();
        entityManager.flush();
        entityManager.clear();
    }

    @Override
    public List<Object[]> sumMonthlyByUserGroupedByCategoryAndType(Long userRowId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        return entityManager.createQuery(
                "SELECT s.category.rowId, e.expenseType, COALESCE(SUM(s.amount), 0) " +
                "FROM ExpenseSplit s JOIN s.expense e " +
                "WHERE e.user.rowId = :userRowId " +
                "  AND e.isDeleted = :isDeleted " +
                "  AND s.isDeleted = :isDeleted " +
                "  AND e.expenseDate >= :start " +
                "  AND e.expenseDate < :end " +
                "GROUP BY s.category.rowId, e.expenseType",
                Object[].class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .setParameter("start", start)
            .setParameter("end", end)
            .getResultList();
    }
}
