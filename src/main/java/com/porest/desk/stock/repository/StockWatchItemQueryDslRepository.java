package com.porest.desk.stock.repository;

import com.porest.core.type.YNType;
import com.porest.desk.stock.domain.QStockMaster;
import com.porest.desk.stock.domain.QStockWatchGroup;
import com.porest.desk.stock.domain.QStockWatchItem;
import com.porest.desk.stock.domain.StockWatchItem;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class StockWatchItemQueryDslRepository implements StockWatchItemRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QStockWatchItem item = QStockWatchItem.stockWatchItem;
    private static final QStockWatchGroup group = QStockWatchGroup.stockWatchGroup;
    private static final QStockMaster stockMaster = QStockMaster.stockMaster;

    @Override
    public List<ItemWithStock> findAllActiveByUserWithStock(Long userRowId) {
        List<Tuple> rows = queryFactory.select(item, stockMaster)
            .from(item)
            .join(group).on(item.groupRowId.eq(group.rowId))
            .join(stockMaster).on(item.stockMasterRowId.eq(stockMaster.rowId))
            .where(
                group.userRowId.eq(userRowId),
                group.isDeleted.eq(YNType.N),
                item.isDeleted.eq(YNType.N)
            )
            .orderBy(group.sortOrder.asc(), group.rowId.asc(), item.sortOrder.asc(), item.rowId.asc())
            .fetch();

        return rows.stream()
            .map(t -> new ItemWithStock(
                Objects.requireNonNull(t.get(item)),
                Objects.requireNonNull(t.get(stockMaster))))
            .toList();
    }

    @Override
    public Optional<StockWatchItem> findByGroupAndStockIncludingDeleted(Long groupRowId, Long stockMasterRowId) {
        StockWatchItem result = queryFactory.selectFrom(item)
            .where(
                item.groupRowId.eq(groupRowId),
                item.stockMasterRowId.eq(stockMasterRowId)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<StockWatchItem> findActiveById(Long itemRowId) {
        StockWatchItem result = queryFactory.selectFrom(item)
            .where(
                item.rowId.eq(itemRowId),
                item.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<StockWatchItem> findAllActiveByGroup(Long groupRowId) {
        return queryFactory.selectFrom(item)
            .where(
                item.groupRowId.eq(groupRowId),
                item.isDeleted.eq(YNType.N)
            )
            .orderBy(item.sortOrder.asc(), item.rowId.asc())
            .fetch();
    }

    @Override
    public long countActiveByGroup(Long groupRowId) {
        Long total = queryFactory.select(item.count())
            .from(item)
            .where(
                item.groupRowId.eq(groupRowId),
                item.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public StockWatchItem save(StockWatchItem entity) {
        entityManager.persist(entity);
        return entity;
    }
}
