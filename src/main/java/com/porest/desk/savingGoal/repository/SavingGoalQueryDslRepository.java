package com.porest.desk.savingGoal.repository;

import com.porest.core.type.YNType;
import com.porest.desk.savingGoal.domain.QSavingGoal;
import com.porest.desk.savingGoal.domain.SavingGoal;
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
public class SavingGoalQueryDslRepository implements SavingGoalRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QSavingGoal savingGoal = QSavingGoal.savingGoal;

    @Override
    public Optional<SavingGoal> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(savingGoal)
                .where(savingGoal.rowId.eq(rowId), savingGoal.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<SavingGoal> findByUser(Long userRowId) {
        return queryFactory.selectFrom(savingGoal)
            .where(savingGoal.user.rowId.eq(userRowId), savingGoal.isDeleted.eq(YNType.N))
            .orderBy(savingGoal.sortOrder.asc(), savingGoal.rowId.asc())
            .fetch();
    }

    @Override
    public SavingGoal save(SavingGoal entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(SavingGoal entity) {
        entity.deleteSavingGoal();
    }
}
