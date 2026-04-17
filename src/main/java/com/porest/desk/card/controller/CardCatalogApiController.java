package com.porest.desk.card.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.core.controller.dto.PageRequest;
import com.porest.core.controller.dto.PageResponse;
import com.porest.desk.card.controller.dto.CardCatalogApiDto;
import com.porest.desk.card.repository.CardCatalogSearchCondition;
import com.porest.desk.card.service.CardCatalogService;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CardCatalogApiController {
    private final CardCatalogService cardCatalogService;

    @GetMapping("/card-catalogs")
    public ApiResponse<PageResponse<CardCatalogApiDto.SummaryResponse>> searchCardCatalogs(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) CardType cardType,
        @RequestParam(required = false) CardBenefitType benefitType,
        @RequestParam(required = false) Boolean includeDiscontinued,
        PageRequest pageRequest
    ) {
        CardCatalogSearchCondition condition = new CardCatalogSearchCondition(
            keyword, cardType, benefitType, includeDiscontinued
        );
        Page<CardCatalogServiceDto.CatalogSummary> page = cardCatalogService.search(condition, pageRequest.toPageable());
        return ApiResponse.success(PageResponse.of(page, CardCatalogApiDto.SummaryResponse::from));
    }

    @GetMapping("/card-catalogs/{id}")
    public ApiResponse<CardCatalogApiDto.DetailResponse> getCardCatalog(@PathVariable Long id) {
        CardCatalogServiceDto.CatalogDetail detail = cardCatalogService.getDetail(id);
        return ApiResponse.success(CardCatalogApiDto.DetailResponse.from(detail));
    }
}
