package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogBrand;
import com.porest.desk.card.domain.QCardCatalogBrand;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class CardCatalogBrandQueryDslRepository implements CardCatalogBrandRepository {
    private final JPAQueryFactory queryFactory;
    private static final QCardCatalogBrand brand = QCardCatalogBrand.cardCatalogBrand;

    @Override
    public List<CardCatalogBrand> findAllByCardCatalog(Long cardCatalogRowId) {
        return queryFactory.selectFrom(brand)
            .where(brand.cardCatalog.rowId.eq(cardCatalogRowId))
            .orderBy(brand.sortOrder.asc(), brand.rowId.asc())
            .fetch();
    }
}
