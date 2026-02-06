package com.porest.desk.calculator.repository;

import com.porest.desk.calculator.domain.CalculatorHistory;
import com.porest.desk.calculator.domain.QCalculatorHistory;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class CalculatorHistoryQueryDslRepository implements CalculatorHistoryRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QCalculatorHistory calculatorHistory = QCalculatorHistory.calculatorHistory;

    @Override
    public List<CalculatorHistory> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(calculatorHistory)
            .where(calculatorHistory.user.rowId.eq(userRowId))
            .orderBy(calculatorHistory.rowId.desc())
            .fetch();
    }

    @Override
    public CalculatorHistory save(CalculatorHistory entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void deleteAllByUser(Long userRowId) {
        queryFactory.delete(calculatorHistory)
            .where(calculatorHistory.user.rowId.eq(userRowId))
            .execute();
    }
}
