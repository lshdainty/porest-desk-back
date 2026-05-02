package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogBrand;

import java.util.List;

public interface CardCatalogBrandRepository {
    List<CardCatalogBrand> findAllByCardCatalog(Long cardCatalogRowId);
}
