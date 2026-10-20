package io.groo.damda.card.repository;

import io.groo.damda.card.type.CardBenefitType;
import io.groo.damda.card.type.CardType;

public record CardCatalogSearchCondition(
    String keyword,
    CardType cardType,
    CardBenefitType benefitType,
    Boolean includeDiscontinued
) {}
