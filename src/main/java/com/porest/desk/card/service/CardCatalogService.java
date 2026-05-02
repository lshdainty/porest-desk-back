package com.porest.desk.card.service;

import com.porest.desk.card.repository.CardCatalogSearchCondition;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CardCatalogService {
    Page<CardCatalogServiceDto.CatalogSummary> search(CardCatalogSearchCondition condition, Pageable pageable);
    CardCatalogServiceDto.CatalogDetail getDetail(Long cardCatalogRowId);
}
