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
                item.isDeleted.eq(YNType.N),
                // 비활성 마스터(상장폐지·심볼 변경으로 파일에서 사라진 종목)는 감춘다.
                // 조인만 걸어 두면 시세가 안 붙는 행이 정상처럼 남는다 — 검색·시세 조회가
                // 전부 is_active=Y 로 거르므로 여기만 예외가 되면 화면끼리 어긋난다.
                // 행 자체는 지우지 않으므로 재상장으로 다시 활성화되면 그대로 돌아온다.
                stockMaster.isActive.eq(YNType.N).not()
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
