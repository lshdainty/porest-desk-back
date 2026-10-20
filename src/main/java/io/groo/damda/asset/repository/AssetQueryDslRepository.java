package io.groo.damda.asset.repository;

import io.groo.core.type.YNType;
import io.groo.damda.asset.domain.Asset;
import io.groo.damda.asset.domain.QAsset;
import io.groo.damda.asset.type.AssetType;
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
    public List<Asset> findAllByType(AssetType assetType) {
        return queryFactory.selectFrom(asset)
            .where(asset.assetType.eq(assetType), asset.isDeleted.eq(YNType.N))
            .orderBy(asset.rowId.asc())
            .fetch();
    }

    @Override
    public List<Long> findUserRowIdsWithAssets() {
        return queryFactory.select(asset.user.rowId)
            .from(asset)
            .where(asset.isDeleted.eq(YNType.N))
            .distinct()
            .orderBy(asset.user.rowId.asc())
            .fetch();
    }

    @Override
    public boolean existsDeletedOwnedBy(Long rowId, Long userRowId) {
        return queryFactory.selectOne()
            .from(asset)
            .where(asset.rowId.eq(rowId), asset.user.rowId.eq(userRowId), asset.isDeleted.eq(YNType.Y))
            .fetchFirst() != null;
    }

    @Override
    public Asset save(Asset entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(Asset entity) {
        entity.deleteAsset();
    }
}
