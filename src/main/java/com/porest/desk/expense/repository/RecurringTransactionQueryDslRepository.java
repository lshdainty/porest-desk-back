package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.QRecurringTransaction;
import com.porest.desk.expense.domain.RecurringTransaction;
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
public class RecurringTransactionQueryDslRepository implements RecurringTransactionRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QRecurringTransaction recurring = QRecurringTransaction.recurringTransaction;

    @Override
    public Optional<RecurringTransaction> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(recurring)
                .where(recurring.rowId.eq(rowId), recurring.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<RecurringTransaction> findByUser(Long userRowId) {
        return queryFactory.selectFrom(recurring)
            .where(
                recurring.user.rowId.eq(userRowId),
                recurring.isDeleted.eq(YNType.N)
            )
            .orderBy(recurring.nextExecutionDate.asc(), recurring.rowId.desc())
            .fetch();
    }

    @Override
    public List<RecurringTransaction> findDueTransactions(LocalDate date) {
        return queryFactory.selectFrom(recurring)
            .where(
                recurring.isActive.eq(YNType.Y),
                recurring.isDeleted.eq(YNType.N),
                recurring.nextExecutionDate.loe(date),
                recurring.endDate.isNull().or(recurring.endDate.goe(date))
            )
            .fetch();
    }

    @Override
    public RecurringTransaction save(RecurringTransaction entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(RecurringTransaction entity) {
        entity.deleteRecurring();
    }
}
