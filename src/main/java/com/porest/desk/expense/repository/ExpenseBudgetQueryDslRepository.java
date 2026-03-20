package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.QExpenseBudget;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class ExpenseBudgetQueryDslRepository implements ExpenseBudgetRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpenseBudget expenseBudget = QExpenseBudget.expenseBudget;

    @Override
    public Optional<ExpenseBudget> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(expenseBudget)
                .where(expenseBudget.rowId.eq(rowId))
                .fetchOne()
        );
    }

    @Override
    public List<ExpenseBudget> findByUser(Long userRowId, Integer year, Integer month) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(expenseBudget.user.rowId.eq(userRowId));

        if (year != null) {
            builder.and(expenseBudget.budgetYear.eq(year));
        }
        if (month != null) {
            builder.and(expenseBudget.budgetMonth.eq(month));
        }

        return queryFactory.selectFrom(expenseBudget)
            .where(builder)
            .orderBy(expenseBudget.budgetYear.desc(), expenseBudget.budgetMonth.desc())
            .fetch();
    }

    @Override
    public List<ExpenseBudget> findAllByYearAndMonth(Integer year, Integer month) {
        return queryFactory.selectFrom(expenseBudget)
            .leftJoin(expenseBudget.user).fetchJoin()
            .leftJoin(expenseBudget.category).fetchJoin()
            .where(
                expenseBudget.budgetYear.eq(year),
                expenseBudget.budgetMonth.eq(month)
            )
            .fetch();
    }

    @Override
    public Optional<ExpenseBudget> findByUserAndCategory(Long userRowId, Long categoryRowId, Integer year, Integer month) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(expenseBudget.user.rowId.eq(userRowId));
        builder.and(expenseBudget.budgetYear.eq(year));
        builder.and(expenseBudget.budgetMonth.eq(month));

        if (categoryRowId != null) {
            builder.and(expenseBudget.category.rowId.eq(categoryRowId));
        } else {
            builder.and(expenseBudget.category.isNull());
        }

        return Optional.ofNullable(
            queryFactory.selectFrom(expenseBudget)
                .where(builder)
                .fetchOne()
        );
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
