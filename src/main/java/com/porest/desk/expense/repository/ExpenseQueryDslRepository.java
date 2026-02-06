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
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class ExpenseQueryDslRepository implements ExpenseRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpense expense = QExpense.expense;

    @Override
    public Optional<Expense> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(expense)
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
            builder.and(expense.expenseDate.goe(startDate));
        }
        if (endDate != null) {
            builder.and(expense.expenseDate.loe(endDate));
        }

        return queryFactory.selectFrom(expense)
            .where(builder)
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findDailySummary(Long userRowId, LocalDate date) {
        return queryFactory.selectFrom(expense)
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.eq(date)
            )
            .orderBy(expense.rowId.desc())
            .fetch();
    }

    @Override
    public List<Expense> findMonthlySummary(Long userRowId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        return queryFactory.selectFrom(expense)
            .where(
                expense.user.rowId.eq(userRowId),
                expense.isDeleted.eq(YNType.N),
                expense.expenseDate.goe(startDate),
                expense.expenseDate.loe(endDate)
            )
            .orderBy(expense.expenseDate.desc(), expense.rowId.desc())
            .fetch();
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
