package com.porest.desk.stock.repository;

import com.porest.core.type.YNType;
import com.porest.desk.stock.domain.QStockMaster;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.type.StockMarket;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class StockMasterQueryDslRepository implements StockMasterRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QStockMaster stockMaster = QStockMaster.stockMaster;

    @Override
    public Page<StockMaster> search(StockMasterSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = buildWhere(condition);

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (hasKeyword(condition.keyword())) {
            orders.add(rankOf(condition.keyword()).asc());
        }
        orders.add(stockMaster.nameKr.asc());
        orders.add(stockMaster.rowId.asc());

        List<StockMaster> items = queryFactory.selectFrom(stockMaster)
            .where(where)
            .orderBy(orders.toArray(new OrderSpecifier<?>[0]))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(stockMaster.count())
            .from(stockMaster)
            .where(where)
            .fetchOne();

        return new PageImpl<>(items, pageable, total == null ? 0L : total);
    }

    @Override
    public List<StockMaster> findAllByMarketIncludingInactive(StockMarket market) {
        return queryFactory.selectFrom(stockMaster)
            .where(stockMaster.marketCode.eq(market))
            .fetch();
    }

    @Override
    public long countAll() {
        Long total = queryFactory.select(stockMaster.count())
            .from(stockMaster)
            .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public StockMaster save(StockMaster entity) {
        entityManager.persist(entity);
        return entity;
    }

    private BooleanBuilder buildWhere(StockMasterSearchCondition condition) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(stockMaster.isDeleted.eq(YNType.N));
        where.and(stockMaster.isActive.eq(YNType.Y));

        if (hasKeyword(condition.keyword())) {
            String kw = condition.keyword().trim();
            where.and(
                stockMaster.nameKr.containsIgnoreCase(kw)
                    .or(stockMaster.nameEn.containsIgnoreCase(kw))
                    .or(stockMaster.symbol.containsIgnoreCase(kw))
            );
        }
        if (condition.countryCode() != null && !condition.countryCode().isBlank()) {
            where.and(stockMaster.countryCode.eq(condition.countryCode().trim().toUpperCase()));
        }
        if (condition.securityType() != null) {
            where.and(stockMaster.securityType.eq(condition.securityType()));
        }
        return where;
    }

    /**
     * 검색어 일치 강도 순 정렬. "애플"로 찾으면 '애플'(AAPL)이 '애플 하스피탤리티'보다 먼저 나오고,
     * "AAPL"처럼 심볼을 그대로 치면 해당 종목이 최상단에 온다.
     */
    private NumberExpression<Integer> rankOf(String keyword) {
        String kw = keyword.trim();
        return new CaseBuilder()
            .when(stockMaster.symbol.equalsIgnoreCase(kw)).then(0)
            .when(stockMaster.nameKr.eq(kw)).then(0)
            .when(stockMaster.nameKr.startsWith(kw)).then(1)
            .when(stockMaster.nameEn.startsWithIgnoreCase(kw)).then(1)
            .when(stockMaster.symbol.startsWithIgnoreCase(kw)).then(1)
            .otherwise(2);
    }

    private boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.isBlank();
    }
}
