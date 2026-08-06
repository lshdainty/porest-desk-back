package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.domain.QCardBilling;
import com.porest.desk.card.type.BillingStatus;
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
public class CardBillingQueryDslRepository implements CardBillingRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QCardBilling billing = QCardBilling.cardBilling;

    @Override
    public CardBilling save(CardBilling entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public List<CardBilling> findByCardAssetRowId(Long cardAssetRowId) {
        return queryFactory.selectFrom(billing)
            .where(billing.cardAsset.rowId.eq(cardAssetRowId), billing.isDeleted.eq(YNType.N))
            .orderBy(billing.paymentDate.desc(), billing.rowId.desc())
            .fetch();
    }

    @Override
    public long sumCompletedAmountByCardAndPeriod(Long cardAssetRowId, LocalDate periodStart, LocalDate periodEnd) {
        // openfeign querydsl 7.x — sum() 집계 대신 fetch 후 합산(ExpenseQueryDslRepository 관례)
        List<Long> amounts = queryFactory.select(billing.billingAmount)
            .from(billing)
            .where(
                billing.cardAsset.rowId.eq(cardAssetRowId),
                billing.periodStart.eq(periodStart),
                billing.periodEnd.eq(periodEnd),
                billing.status.eq(BillingStatus.COMPLETED),
                billing.isDeleted.eq(YNType.N)
            )
            .fetch();
        return amounts.stream().filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    @Override
    public boolean existsCompletedByCardAndPaymentDate(Long cardAssetRowId, LocalDate paymentDate) {
        Integer fetched = queryFactory.selectOne()
            .from(billing)
            .where(
                billing.cardAsset.rowId.eq(cardAssetRowId),
                billing.paymentDate.eq(paymentDate),
                billing.status.eq(BillingStatus.COMPLETED),
                billing.isDeleted.eq(YNType.N)
            )
            .fetchFirst();
        return fetched != null;
    }

    @Override
    public Optional<CardBilling> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(billing)
                .leftJoin(billing.cardAsset).fetchJoin()
                .leftJoin(billing.transfer).fetchJoin()
                .where(billing.rowId.eq(rowId))
                .fetchOne());
    }

    @Override
    public Optional<CardBilling> findActiveByTransfer(Long transferRowId) {
        return Optional.ofNullable(queryFactory.selectFrom(billing)
            .where(billing.transfer.rowId.eq(transferRowId), billing.isDeleted.eq(YNType.N))
            .fetchFirst());
    }

    @Override
    public List<CardBilling> findByStatus(BillingStatus status) {
        return queryFactory.selectFrom(billing)
            .where(billing.status.eq(status), billing.isDeleted.eq(YNType.N))
            .orderBy(billing.paymentDate.desc(), billing.rowId.desc())
            .fetch();
    }
}
