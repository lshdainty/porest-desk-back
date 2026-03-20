package com.porest.desk.dutchpay.repository;

import com.porest.core.type.YNType;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.QDutchPay;
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
public class DutchPayQueryDslRepository implements DutchPayRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QDutchPay dutchPay = QDutchPay.dutchPay;

    @Override
    public Optional<DutchPay> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(dutchPay)
                .leftJoin(dutchPay.participants).fetchJoin()
                .where(dutchPay.rowId.eq(rowId), dutchPay.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<DutchPay> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(dutchPay)
            .leftJoin(dutchPay.participants).fetchJoin()
            .where(dutchPay.user.rowId.eq(userRowId), dutchPay.isDeleted.eq(YNType.N))
            .orderBy(dutchPay.dutchPayDate.desc(), dutchPay.rowId.desc())
            .distinct()
            .fetch();
    }

    @Override
    public DutchPay save(DutchPay entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(DutchPay entity) {
        entityManager.remove(entity);
    }
}
