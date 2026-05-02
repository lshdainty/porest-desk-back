package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CardCatalogRepository {
    Optional<CardCatalog> findById(Long rowId);
    Page<CardCatalog> search(CardCatalogSearchCondition condition, Pageable pageable);
}
