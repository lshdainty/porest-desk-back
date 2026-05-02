package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.domain.CardCatalogBenefit;
import com.porest.desk.card.domain.CardCatalogBrand;
import com.porest.desk.card.domain.CardCatalogTag;
import com.porest.desk.card.repository.CardCatalogBenefitRepository;
import com.porest.desk.card.repository.CardCatalogBrandRepository;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.card.repository.CardCatalogSearchCondition;
import com.porest.desk.card.repository.CardCatalogTagRepository;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
import com.porest.desk.card.type.CardBenefitKind;
import com.porest.desk.card.type.CardTagKind;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CardCatalogServiceImpl implements CardCatalogService {
    private final CardCatalogRepository cardCatalogRepository;
    private final CardCatalogBrandRepository cardCatalogBrandRepository;
    private final CardCatalogBenefitRepository cardCatalogBenefitRepository;
    private final CardCatalogTagRepository cardCatalogTagRepository;

    @Override
    public Page<CardCatalogServiceDto.CatalogSummary> search(CardCatalogSearchCondition condition, Pageable pageable) {
        log.debug("카드 카탈로그 검색: condition={}, pageable={}", condition, pageable);
        Page<CardCatalog> page = cardCatalogRepository.search(condition, pageable);
        return page.map(CardCatalogServiceDto.CatalogSummary::from);
    }

    @Override
    public CardCatalogServiceDto.CatalogDetail getDetail(Long cardCatalogRowId) {
        log.debug("카드 카탈로그 상세 조회: rowId={}", cardCatalogRowId);

        CardCatalog catalog = cardCatalogRepository.findById(cardCatalogRowId)
            .orElseThrow(() -> {
                log.warn("카드 카탈로그 조회 실패 - 존재하지 않는 카드: rowId={}", cardCatalogRowId);
                return new EntityNotFoundException(DeskErrorCode.CARD_CATALOG_NOT_FOUND);
            });

        List<CardCatalogBrand> brands = cardCatalogBrandRepository.findAllByCardCatalog(cardCatalogRowId);
        List<CardCatalogBenefit> allBenefits = cardCatalogBenefitRepository.findAllByCardCatalog(cardCatalogRowId);
        List<CardCatalogTag> allTags = cardCatalogTagRepository.findAllByCardCatalog(cardCatalogRowId);

        List<CardCatalogBenefit> benefits = allBenefits.stream().filter(b -> b.getKind() == CardBenefitKind.BENEFIT).toList();
        List<CardCatalogBenefit> cautions = allBenefits.stream().filter(b -> b.getKind() == CardBenefitKind.CAUTION).toList();
        List<CardCatalogTag> topTags = allTags.stream().filter(t -> t.getKind() == CardTagKind.TOP).toList();
        List<CardCatalogTag> searchTags = allTags.stream().filter(t -> t.getKind() == CardTagKind.SEARCH).toList();

        return CardCatalogServiceDto.CatalogDetail.of(catalog, brands, benefits, cautions, topTags, searchTags);
    }
}
