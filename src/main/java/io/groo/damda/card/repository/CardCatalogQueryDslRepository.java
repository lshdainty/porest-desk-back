package io.groo.damda.card.repository;

import io.groo.core.type.YNType;
import io.groo.damda.common.search.SearchKeyword;
import io.groo.damda.card.domain.CardCatalog;
import io.groo.damda.card.domain.QCardCatalog;
import io.groo.damda.card.domain.QCardCompany;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class CardCatalogQueryDslRepository implements CardCatalogRepository {
    private final JPAQueryFactory queryFactory;
    private static final QCardCatalog cardCatalog = QCardCatalog.cardCatalog;
    private static final QCardCompany company = QCardCompany.cardCompany;

    @Override
    public Optional<CardCatalog> findById(Long rowId) {
        CardCatalog result = queryFactory.selectFrom(cardCatalog)
            .leftJoin(cardCatalog.company, company).fetchJoin()
            .where(
                cardCatalog.rowId.eq(rowId),
                cardCatalog.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Page<CardCatalog> search(CardCatalogSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = buildWhere(condition);

        List<CardCatalog> items = queryFactory.selectFrom(cardCatalog)
            .leftJoin(cardCatalog.company, company).fetchJoin()
            .where(where)
            .orderBy(cardCatalog.isDiscontinued.asc(), cardCatalog.rowId.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(cardCatalog.count())
            .from(cardCatalog)
            .leftJoin(cardCatalog.company, company)
            .where(where)
            .fetchOne();

        return new PageImpl<>(items, pageable, total == null ? 0L : total);
    }

    private BooleanBuilder buildWhere(CardCatalogSearchCondition condition) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(cardCatalog.isDeleted.eq(YNType.N));

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            String kw = SearchKeyword.normalize(condition.keyword());
            where.and(
                SearchKeyword.contains(cardCatalog.cardName, kw)
                    .or(SearchKeyword.contains(company.name, kw))
            );
        }
        if (condition.cardType() != null) {
            where.and(cardCatalog.cardType.eq(condition.cardType()));
        }
        if (condition.benefitType() != null) {
            where.and(cardCatalog.benefitType.eq(condition.benefitType()));
        }
        if (condition.includeDiscontinued() == null || !condition.includeDiscontinued()) {
            where.and(cardCatalog.isDiscontinued.eq(YNType.N));
        }

        return where;
    }
}
