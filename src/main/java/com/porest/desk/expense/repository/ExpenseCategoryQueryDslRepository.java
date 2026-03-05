package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.QExpenseCategory;
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
public class ExpenseCategoryQueryDslRepository implements ExpenseCategoryRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpenseCategory expenseCategory = QExpenseCategory.expenseCategory;

    @Override
    public Optional<ExpenseCategory> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(expenseCategory)
                .where(expenseCategory.rowId.eq(rowId), expenseCategory.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<ExpenseCategory> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(expenseCategory)
            .where(
                expenseCategory.user.rowId.eq(userRowId),
                expenseCategory.isDeleted.eq(YNType.N)
            )
            .orderBy(expenseCategory.sortOrder.asc(), expenseCategory.rowId.asc())
            .fetch();
    }

    @Override
    public ExpenseCategory save(ExpenseCategory entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(ExpenseCategory entity) {
        entity.deleteCategory();
    }

    @Override
    public boolean hasChildren(Long categoryRowId) {
        return queryFactory.selectOne()
            .from(expenseCategory)
            .where(
                expenseCategory.parent.rowId.eq(categoryRowId),
                expenseCategory.isDeleted.eq(YNType.N)
            )
            .fetchFirst() != null;
    }
}
