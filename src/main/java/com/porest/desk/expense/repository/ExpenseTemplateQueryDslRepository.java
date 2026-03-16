package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.ExpenseTemplate;
import com.porest.desk.expense.domain.QExpenseTemplate;
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
public class ExpenseTemplateQueryDslRepository implements ExpenseTemplateRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QExpenseTemplate template = QExpenseTemplate.expenseTemplate;

    @Override
    public Optional<ExpenseTemplate> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(template)
                .where(template.rowId.eq(rowId), template.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<ExpenseTemplate> findByUser(Long userRowId) {
        return queryFactory.selectFrom(template)
            .where(
                template.user.rowId.eq(userRowId),
                template.isDeleted.eq(YNType.N)
            )
            .orderBy(template.useCount.desc(), template.sortOrder.asc(), template.rowId.desc())
            .fetch();
    }

    @Override
    public ExpenseTemplate save(ExpenseTemplate entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(ExpenseTemplate entity) {
        entity.deleteTemplate();
    }
}
