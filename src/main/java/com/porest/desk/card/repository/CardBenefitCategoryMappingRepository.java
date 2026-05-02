package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardBenefitCategoryMapping;

import java.util.List;
import java.util.Optional;

public interface CardBenefitCategoryMappingRepository {
    Optional<CardBenefitCategoryMapping> findById(Long rowId);

    /**
     * 사용자 커스텀 매핑 조회 (해당 benefit_category 키로)
     */
    Optional<CardBenefitCategoryMapping> findUserMapping(Long userRowId, String benefitCategory);

    /**
     * Effective 매핑 목록: 공용(user_row_id IS NULL) + 사용자 커스텀 merge.
     * 동일 benefit_category 에 대해 사용자 커스텀이 있으면 그것이 우선.
     */
    List<CardBenefitCategoryMapping> findEffectiveMappings(Long userRowId);

    /**
     * 공용 기본 매핑 전체 (관리용)
     */
    List<CardBenefitCategoryMapping> findAllDefaultMappings();

    CardBenefitCategoryMapping save(CardBenefitCategoryMapping mapping);
}
