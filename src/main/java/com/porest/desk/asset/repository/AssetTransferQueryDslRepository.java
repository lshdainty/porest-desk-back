package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.domain.QAssetTransfer;
import com.querydsl.core.BooleanBuilder;
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
public class AssetTransferQueryDslRepository implements AssetTransferRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAssetTransfer transfer = QAssetTransfer.assetTransfer;

    @Override
    public Optional<AssetTransfer> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(transfer)
                .where(transfer.rowId.eq(rowId), transfer.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<AssetTransfer> findByUser(Long userRowId, LocalDate startDate, LocalDate endDate) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(transfer.user.rowId.eq(userRowId));
        builder.and(transfer.isDeleted.eq(YNType.N));

        if (startDate != null) {
            builder.and(transfer.transferDate.goe(startDate));
        }
        if (endDate != null) {
            builder.and(transfer.transferDate.loe(endDate));
        }

        return queryFactory.selectFrom(transfer)
            .where(builder)
            .orderBy(transfer.transferDate.desc(), transfer.rowId.desc())
            .fetch();
    }

    @Override
    public AssetTransfer save(AssetTransfer entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(AssetTransfer entity) {
        entity.deleteTransfer();
    }

    @Override
    public List<Object[]> sumMonthlyTransferInByUserGroupedByAsset(Long userRowId, LocalDate endDate) {
        return entityManager.createQuery(
                "SELECT t.toAsset.rowId, " +
                "       YEAR(t.transferDate), " +
                "       MONTH(t.transferDate), " +
                "       COALESCE(SUM(t.amount), 0) " +
                "FROM AssetTransfer t " +
                "WHERE t.user.rowId = :userRowId " +
                "  AND t.transferDate <= :endDate " +
                "  AND t.isDeleted = :isDeleted " +
                "GROUP BY t.toAsset.rowId, YEAR(t.transferDate), MONTH(t.transferDate)",
                Object[].class)
            .setParameter("userRowId", userRowId)
            .setParameter("endDate", endDate)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Object[]> sumMonthlyTransferOutByUserGroupedByAsset(Long userRowId, LocalDate endDate) {
        return entityManager.createQuery(
                "SELECT t.fromAsset.rowId, " +
                "       YEAR(t.transferDate), " +
                "       MONTH(t.transferDate), " +
                "       COALESCE(SUM(t.amount + t.fee), 0) " +
                "FROM AssetTransfer t " +
                "WHERE t.user.rowId = :userRowId " +
                "  AND t.transferDate <= :endDate " +
                "  AND t.isDeleted = :isDeleted " +
                "GROUP BY t.fromAsset.rowId, YEAR(t.transferDate), MONTH(t.transferDate)",
                Object[].class)
            .setParameter("userRowId", userRowId)
            .setParameter("endDate", endDate)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public List<Object[]> sumAllTransferByAssetGroupedByWeek(Long assetRowId, String direction) {
        boolean isIn = "IN".equalsIgnoreCase(direction);
        String assetPath = isIn ? "t.toAsset.rowId" : "t.fromAsset.rowId";
        String amountExpr = isIn ? "t.amount" : "(t.amount + t.fee)";
        String jpql =
            "SELECT FUNCTION('YEARWEEK', t.transferDate, 3), COALESCE(SUM(" + amountExpr + "), 0) " +
            "FROM AssetTransfer t " +
            "WHERE " + assetPath + " = :assetRowId " +
            "  AND t.isDeleted = :isDeleted " +
            "GROUP BY FUNCTION('YEARWEEK', t.transferDate, 3)";
        return entityManager.createQuery(jpql, Object[].class)
            .setParameter("assetRowId", assetRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }
}
