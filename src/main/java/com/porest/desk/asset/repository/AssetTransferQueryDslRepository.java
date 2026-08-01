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

        // transfer_date 가 DATETIME 이 되면서 날짜 범위 필터는 경계를 하루로 넓혀 비교한다.
        // (endDate 를 그대로 loe 하면 그날 00:00 이후 이체가 전부 빠진다)
        if (startDate != null) {
            builder.and(transfer.transferDate.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            builder.and(transfer.transferDate.lt(endDate.plusDays(1).atStartOfDay()));
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
}
