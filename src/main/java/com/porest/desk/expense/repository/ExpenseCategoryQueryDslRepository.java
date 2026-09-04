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
            .leftJoin(expenseCategory.parent).fetchJoin()
            .where(
                expenseCategory.user.rowId.eq(userRowId),
                expenseCategory.isDeleted.eq(YNType.N)
            )
            .orderBy(expenseCategory.sortOrder.asc(), expenseCategory.rowId.asc())
            .fetch();
    }

    @Override
    public ExpenseCategory save(ExpenseCategory entity) {
        entityManager.persist(entity);
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

    @Override
    public boolean existsActiveByUserAndParentAndTypeAndName(Long userRowId, Long parentRowId,
                                                             com.porest.desk.expense.type.ExpenseType expenseType,
                                                             String categoryName, Long excludeRowId) {
        return queryFactory.selectOne()
            .from(expenseCategory)
            .where(
                expenseCategory.user.rowId.eq(userRowId),
                expenseCategory.categoryName.eq(categoryName),
                expenseCategory.expenseType.eq(expenseType),
                expenseCategory.isDeleted.eq(YNType.N),
                parentRowId != null ? expenseCategory.parent.rowId.eq(parentRowId) : expenseCategory.parent.isNull(),
                excludeRowId != null ? expenseCategory.rowId.ne(excludeRowId) : null
            )
            .fetchFirst() != null;
    }

    @Override
    public Optional<ExpenseCategory> findActiveByUserAndParentAndTypeAndName(
            Long userRowId, Long parentRowId,
            com.porest.desk.expense.type.ExpenseType expenseType, String categoryName) {
        // fetchOne 이 아니라 fetchFirst 다 — 유니크가 붙기 전 데이터에 같은 자리 활성 행이
        // 둘 있으면 fetchOne 은 NonUniqueResultException 을 던지고, 그러면 가져오기가
        // 정리되지 않은 계정에서 통째로 막힌다.
        return Optional.ofNullable(
            queryFactory.selectFrom(expenseCategory)
                .where(
                    expenseCategory.user.rowId.eq(userRowId),
                    expenseCategory.categoryName.eq(categoryName),
                    expenseCategory.expenseType.eq(expenseType),
                    expenseCategory.isDeleted.eq(YNType.N),
                    parentRowId != null ? expenseCategory.parent.rowId.eq(parentRowId) : expenseCategory.parent.isNull()
                )
                .orderBy(expenseCategory.rowId.asc())
                .fetchFirst()
        );
    }

    @Override
    public void flush() {
        entityManager.flush();
    }
}
