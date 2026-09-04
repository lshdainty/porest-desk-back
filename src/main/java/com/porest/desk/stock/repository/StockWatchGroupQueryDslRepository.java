package com.porest.desk.stock.repository;

import com.porest.core.type.YNType;
import com.porest.desk.stock.domain.QStockWatchGroup;
import com.porest.desk.stock.domain.StockWatchGroup;
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
public class StockWatchGroupQueryDslRepository implements StockWatchGroupRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QStockWatchGroup group = QStockWatchGroup.stockWatchGroup;

    @Override
    public List<StockWatchGroup> findAllActiveByUser(Long userRowId) {
        return queryFactory.selectFrom(group)
            .where(
                group.userRowId.eq(userRowId),
                group.isDeleted.eq(YNType.N)
            )
            .orderBy(group.sortOrder.asc(), group.rowId.asc())
            .fetch();
    }

    @Override
    public Optional<StockWatchGroup> findActiveByIdAndUser(Long groupRowId, Long userRowId) {
        StockWatchGroup result = queryFactory.selectFrom(group)
            .where(
                group.rowId.eq(groupRowId),
                group.userRowId.eq(userRowId),
                group.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public long countActiveByUser(Long userRowId) {
        Long total = queryFactory.select(group.count())
            .from(group)
            .where(
                group.userRowId.eq(userRowId),
                group.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public boolean existsActiveByUserAndName(Long userRowId, String groupName, Long excludeRowId) {
        Long found = queryFactory.select(group.rowId)
            .from(group)
            .where(
                group.userRowId.eq(userRowId),
                group.groupName.eq(groupName),
                group.isDeleted.eq(YNType.N),
                excludeRowId != null ? group.rowId.ne(excludeRowId) : null
            )
            .fetchFirst();
        return found != null;
    }

    @Override
    public StockWatchGroup save(StockWatchGroup entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void flush() {
        entityManager.flush();
    }
}
