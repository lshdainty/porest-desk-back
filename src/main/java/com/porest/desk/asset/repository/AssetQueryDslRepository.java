package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.QAsset;
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
public class AssetQueryDslRepository implements AssetRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAsset asset = QAsset.asset;

    @Override
    public Optional<Asset> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(asset)
                .where(asset.rowId.eq(rowId), asset.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Asset> findByUser(Long userRowId) {
        return queryFactory.selectFrom(asset)
            .where(asset.user.rowId.eq(userRowId), asset.isDeleted.eq(YNType.N))
            .orderBy(asset.sortOrder.asc(), asset.rowId.asc())
            .fetch();
    }

    @Override
    public Asset save(Asset entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(Asset entity) {
        entity.deleteAsset();
    }
}
