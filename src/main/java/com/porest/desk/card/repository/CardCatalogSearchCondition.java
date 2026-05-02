package com.porest.desk.card.repository;

import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;

public record CardCatalogSearchCondition(
    String keyword,
    CardType cardType,
    CardBenefitType benefitType,
    Boolean includeDiscontinued
) {}
