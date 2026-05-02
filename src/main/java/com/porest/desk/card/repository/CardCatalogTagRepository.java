package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogTag;

import java.util.List;

public interface CardCatalogTagRepository {
    List<CardCatalogTag> findAllByCardCatalog(Long cardCatalogRowId);
}
