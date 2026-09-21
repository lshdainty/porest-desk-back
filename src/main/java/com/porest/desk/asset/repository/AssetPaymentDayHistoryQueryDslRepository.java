package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetPaymentDayHistory;
import com.porest.desk.asset.domain.QAssetPaymentDayHistory;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class AssetPaymentDayHistoryQueryDslRepository implements AssetPaymentDayHistoryRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAssetPaymentDayHistory history = QAssetPaymentDayHistory.assetPaymentDayHistory;

    @Override
    public AssetPaymentDayHistory save(AssetPaymentDayHistory entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public List<AssetPaymentDayHistory> findActiveByAsset(Long assetRowId) {
        return queryFactory.selectFrom(history)
            .where(history.asset.rowId.eq(assetRowId), history.isDeleted.eq(YNType.N))
            .orderBy(history.effectiveFromPeriod.asc(), history.rowId.asc())
            .fetch();
    }
}
