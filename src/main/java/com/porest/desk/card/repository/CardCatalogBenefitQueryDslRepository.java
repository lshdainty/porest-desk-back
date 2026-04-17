package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogBenefit;
import com.porest.desk.card.domain.QCardCatalogBenefit;
import com.porest.desk.card.type.CardBenefitKind;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class CardCatalogBenefitQueryDslRepository implements CardCatalogBenefitRepository {
    private final JPAQueryFactory queryFactory;
    private static final QCardCatalogBenefit benefit = QCardCatalogBenefit.cardCatalogBenefit;

    @Override
    public List<CardCatalogBenefit> findAllByCardCatalog(Long cardCatalogRowId) {
        return queryFactory.selectFrom(benefit)
            .where(benefit.cardCatalog.rowId.eq(cardCatalogRowId))
            .orderBy(benefit.kind.asc(), benefit.sortOrder.asc(), benefit.rowId.asc())
            .fetch();
    }

    @Override
    public List<CardCatalogBenefit> findBenefitsByCardAndCategories(Long cardCatalogRowId, List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return queryFactory.selectFrom(benefit)
            .where(
                benefit.cardCatalog.rowId.eq(cardCatalogRowId),
                benefit.kind.eq(CardBenefitKind.BENEFIT),
                benefit.category.in(categories)
            )
            .orderBy(benefit.sortOrder.asc(), benefit.rowId.asc())
            .fetch();
    }
}
