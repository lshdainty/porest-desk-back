package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogTag;
import com.porest.desk.card.domain.QCardCatalogTag;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
@RequiredArgsConstructor
public class CardCatalogTagQueryDslRepository implements CardCatalogTagRepository {
    private final JPAQueryFactory queryFactory;
    private static final QCardCatalogTag tag = QCardCatalogTag.cardCatalogTag;

    @Override
    public List<CardCatalogTag> findAllByCardCatalog(Long cardCatalogRowId) {
        return queryFactory.selectFrom(tag)
            .where(tag.cardCatalog.rowId.eq(cardCatalogRowId))
            .orderBy(tag.kind.asc(), tag.sortOrder.asc(), tag.rowId.asc())
            .fetch();
    }
}
