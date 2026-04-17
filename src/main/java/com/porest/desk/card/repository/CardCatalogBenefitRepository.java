package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalogBenefit;

import java.util.List;

public interface CardCatalogBenefitRepository {
    List<CardCatalogBenefit> findAllByCardCatalog(Long cardCatalogRowId);
    List<CardCatalogBenefit> findBenefitsByCardAndCategories(Long cardCatalogRowId, List<String> categories);
}
