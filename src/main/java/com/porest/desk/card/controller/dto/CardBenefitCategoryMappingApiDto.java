package com.porest.desk.card.controller.dto;

import com.porest.desk.card.service.dto.CardBenefitCategoryMappingServiceDto;

import java.util.List;

public class CardBenefitCategoryMappingApiDto {

    public record CreateRequest(String benefitCategory, Long expenseCategoryRowId) {}

    public record MappingResponse(
        Long rowId,
        String benefitCategory,
        Long expenseCategoryRowId,
        String expenseCategoryName,
        boolean isCustom
    ) {
        public static MappingResponse from(CardBenefitCategoryMappingServiceDto.MappingInfo info) {
            return new MappingResponse(
                info.rowId(),
                info.benefitCategory(),
                info.expenseCategoryRowId(),
                info.expenseCategoryName(),
                info.isCustom()
            );
        }
    }

    public record ListResponse(List<MappingResponse> mappings) {
        public static ListResponse from(List<CardBenefitCategoryMappingServiceDto.MappingInfo> infos) {
            return new ListResponse(infos.stream().map(MappingResponse::from).toList());
        }
    }
}
