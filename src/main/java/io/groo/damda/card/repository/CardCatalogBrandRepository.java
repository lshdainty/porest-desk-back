package io.groo.damda.card.repository;

import io.groo.damda.card.domain.CardCatalogBrand;

import java.util.List;

public interface CardCatalogBrandRepository {
    List<CardCatalogBrand> findAllByCardCatalog(Long cardCatalogRowId);
}
