package io.groo.damda.stock.repository;

import io.groo.core.type.YNType;
import io.groo.damda.common.search.SearchKeyword;
import io.groo.damda.stock.domain.QStockMaster;
import io.groo.damda.stock.domain.StockMaster;
import io.groo.damda.stock.type.StockMarket;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    public Optional<StockMaster> findActiveByMarketAndSymbol(StockMarket market, String symbol) {
        StockMaster result = queryFactory.selectFrom(stockMaster)
            .where(
                stockMaster.marketCode.eq(market),
                stockMaster.symbol.eq(symbol),
                stockMaster.isActive.eq(YNType.Y),
                stockMaster.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<StockMaster> findAllActiveBySymbol(String symbol) {
        return queryFactory.selectFrom(stockMaster)
            .where(
                stockMaster.symbol.eq(symbol),
                stockMaster.isActive.eq(YNType.Y),
                stockMaster.isDeleted.eq(YNType.N)
            )
            .fetch();
    }

    @Override
    public List<StockMaster> findAllActiveByCountryCodeIn(Collection<String> countryCodes) {
        if (countryCodes == null || countryCodes.isEmpty()) {
            return List.of();
        }
        return queryFactory.selectFrom(stockMaster)
            .where(stockMaster.countryCode.in(countryCodes)
                .and(stockMaster.isDeleted.eq(YNType.N))
                .and(stockMaster.isActive.eq(YNType.Y)))
            .fetch();
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
            String kw = SearchKeyword.normalize(condition.keyword());
            where.and(
                SearchKeyword.contains(stockMaster.nameKr, kw)
                    .or(SearchKeyword.contains(stockMaster.nameEn, kw))
                    .or(SearchKeyword.contains(stockMaster.symbol, kw))
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
        String kw = SearchKeyword.normalize(keyword);
        return new CaseBuilder()
            .when(SearchKeyword.equalsTo(stockMaster.symbol, kw)).then(0)
            .when(SearchKeyword.equalsTo(stockMaster.nameKr, kw)).then(0)
            .when(SearchKeyword.startsWith(stockMaster.nameKr, kw)).then(1)
            .when(SearchKeyword.startsWith(stockMaster.nameEn, kw)).then(1)
            .when(SearchKeyword.startsWith(stockMaster.symbol, kw)).then(1)
            .otherwise(2);
    }

    private boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.isBlank();
    }
}
