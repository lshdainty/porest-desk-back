package io.groo.damda.card.repository;

import io.groo.damda.card.domain.CardCatalogTag;

import java.util.List;

public interface CardCatalogTagRepository {
    List<CardCatalogTag> findAllByCardCatalog(Long cardCatalogRowId);
}
