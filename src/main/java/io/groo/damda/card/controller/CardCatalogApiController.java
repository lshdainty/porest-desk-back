package io.groo.damda.card.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.controller.dto.PageRequest;
import io.groo.core.controller.dto.PageResponse;
import io.groo.damda.card.controller.dto.CardCatalogApiDto;
import io.groo.damda.card.repository.CardCatalogSearchCondition;
import io.groo.damda.card.service.CardCatalogService;
import io.groo.damda.card.service.dto.CardCatalogServiceDto;
import io.groo.damda.card.type.CardBenefitType;
import io.groo.damda.card.type.CardType;
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
