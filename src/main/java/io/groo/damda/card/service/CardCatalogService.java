package io.groo.damda.card.service;

import io.groo.damda.card.repository.CardCatalogSearchCondition;
import io.groo.damda.card.service.dto.CardCatalogServiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CardCatalogService {
    Page<CardCatalogServiceDto.CatalogSummary> search(CardCatalogSearchCondition condition, Pageable pageable);
    CardCatalogServiceDto.CatalogDetail getDetail(Long cardCatalogRowId);
}
