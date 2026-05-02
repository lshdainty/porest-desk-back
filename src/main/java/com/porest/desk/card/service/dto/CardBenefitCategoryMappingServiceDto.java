package com.porest.desk.card.service.dto;

import com.porest.desk.card.domain.CardBenefitCategoryMapping;

public class CardBenefitCategoryMappingServiceDto {

    public record CreateCommand(Long userRowId, String benefitCategory, Long expenseCategoryRowId) {}

    public record UpdateCommand(Long expenseCategoryRowId) {}

    public record MappingInfo(
        Long rowId,
        String benefitCategory,
        Long expenseCategoryRowId,
        String expenseCategoryName,
        boolean isCustom
    ) {
        public static MappingInfo from(CardBenefitCategoryMapping m) {
            boolean isCustom = m.getUser() != null;
            String expCatName = m.getExpenseCategory() != null ? m.getExpenseCategory().getCategoryName() : null;
            Long expCatRowId = m.getExpenseCategory() != null ? m.getExpenseCategory().getRowId() : null;
            return new MappingInfo(m.getRowId(), m.getBenefitCategory(), expCatRowId, expCatName, isCustom);
        }
    }
}
