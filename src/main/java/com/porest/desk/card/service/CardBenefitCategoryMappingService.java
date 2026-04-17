package com.porest.desk.card.service;

import com.porest.desk.card.service.dto.CardBenefitCategoryMappingServiceDto;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;

import java.util.List;

public interface CardBenefitCategoryMappingService {
    List<CardBenefitCategoryMappingServiceDto.MappingInfo> getEffectiveMappings(Long userRowId);
    CardBenefitCategoryMappingServiceDto.MappingInfo upsertMapping(CardBenefitCategoryMappingServiceDto.CreateCommand command);
    void deleteMapping(Long mappingRowId, Long userRowId);

    /**
     * 특정 카드의 특정 경비 카테고리에 적용될 수 있는 혜택 목록.
     * (user의 effective 매핑을 참조해서 benefit_category 들을 찾고, 해당 카드의 benefits에서 매칭)
     */
    List<CardCatalogServiceDto.BenefitInfo> getAvailableBenefits(Long userRowId, Long cardCatalogRowId, Long expenseCategoryRowId);
}
