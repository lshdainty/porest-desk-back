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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 카드 카탈로그 조회 서비스 단위 테스트.
 *
 * <p>repository 는 모두 mock — {@link CardCatalogServiceImpl} 의 검색 결과 매핑과
 * 상세 조회 시 혜택/주의/태그 종류(kind)별 분류·그룹핑 정확성만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CardCatalogServiceImplTest {

    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private CardCatalogBrandRepository cardCatalogBrandRepository;
    @Mock private CardCatalogBenefitRepository cardCatalogBenefitRepository;
    @Mock private CardCatalogTagRepository cardCatalogTagRepository;

    @InjectMocks private CardCatalogServiceImpl sut;

    private CardCatalogBenefit benefit(CardBenefitKind kind) {
        CardCatalogBenefit b = mock(CardCatalogBenefit.class);
        given(b.getKind()).willReturn(kind);
        return b;
    }

    private CardCatalogTag tag(CardTagKind kind, String category, String text) {
        CardCatalogTag t = mock(CardCatalogTag.class);
        given(t.getKind()).willReturn(kind);
        given(t.getCategory()).willReturn(category);
        given(t.getTagText()).willReturn(text);
        return t;
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("검색 결과 CardCatalog 를 CatalogSummary 로 매핑한다")
        void mapsCatalogToSummary() {
            CardCatalogSearchCondition condition = new CardCatalogSearchCondition(null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);
            CardCatalog catalog = mock(CardCatalog.class);
            given(catalog.getRowId()).willReturn(1L);
            given(catalog.getCardName()).willReturn("포레스트 카드");
            given(cardCatalogRepository.search(condition, pageable))
                    .willReturn(new PageImpl<>(List.of(catalog), pageable, 1));

            Page<CardCatalogServiceDto.CatalogSummary> result = sut.search(condition, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).rowId()).isEqualTo(1L);
            assertThat(result.getContent().get(0).cardName()).isEqualTo("포레스트 카드");
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 페이지를 반환한다")
        void returnsEmptyPage() {
            CardCatalogSearchCondition condition = new CardCatalogSearchCondition(null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);
            given(cardCatalogRepository.search(condition, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            Page<CardCatalogServiceDto.CatalogSummary> result = sut.search(condition, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getDetail")
    class GetDetail {

        @Test
        @DisplayName("혜택/주의/태그를 kind 로 분류하고 태그는 카테고리별로 그룹핑한다")
        void classifiesByKindAndGroupsTags() {
            long cardId = 1L;
            CardCatalog catalog = mock(CardCatalog.class);
            given(catalog.getRowId()).willReturn(cardId);
            given(cardCatalogRepository.findById(cardId)).willReturn(Optional.of(catalog));

            CardCatalogBrand brand = mock(CardCatalogBrand.class);
            given(brand.getBrand()).willReturn("VISA");
            given(cardCatalogBrandRepository.findAllByCardCatalog(cardId)).willReturn(List.of(brand));

            // 혜택 2건(BENEFIT) + 주의 1건(CAUTION) 섞어서 반환 → 서비스가 분리해야 한다
            CardCatalogBenefit benefit1 = benefit(CardBenefitKind.BENEFIT);
            CardCatalogBenefit caution1 = benefit(CardBenefitKind.CAUTION);
            CardCatalogBenefit benefit2 = benefit(CardBenefitKind.BENEFIT);
            given(cardCatalogBenefitRepository.findAllByCardCatalog(cardId))
                    .willReturn(List.of(benefit1, caution1, benefit2));

            CardCatalogTag topTag1 = tag(CardTagKind.TOP, "여행", "공항 라운지");
            CardCatalogTag topTag2 = tag(CardTagKind.TOP, "여행", "해외 수수료 면제");
            CardCatalogTag searchTag1 = tag(CardTagKind.SEARCH, "쇼핑", "온라인 할인");
            given(cardCatalogTagRepository.findAllByCardCatalog(cardId))
                    .willReturn(List.of(topTag1, topTag2, searchTag1));

            CardCatalogServiceDto.CatalogDetail detail = sut.getDetail(cardId);

            assertThat(detail.summary().rowId()).isEqualTo(cardId);
            assertThat(detail.brands()).containsExactly("VISA");
            assertThat(detail.benefits()).hasSize(2);
            assertThat(detail.cautions()).hasSize(1);
            // TOP 태그는 모두 "여행" 카테고리 → 그룹 1개, 태그 2개
            assertThat(detail.topBenefits()).hasSize(1);
            assertThat(detail.topBenefits().get(0).category()).isEqualTo("여행");
            assertThat(detail.topBenefits().get(0).tags()).containsExactly("공항 라운지", "해외 수수료 면제");
            assertThat(detail.searchBenefits()).hasSize(1);
            assertThat(detail.searchBenefits().get(0).category()).isEqualTo("쇼핑");
        }

        @Test
        @DisplayName("브랜드/혜택/태그가 없으면 상세는 빈 목록으로 채워진다")
        void emptyCollectionsProduceEmptyDetail() {
            long cardId = 2L;
            CardCatalog catalog = mock(CardCatalog.class);
            given(catalog.getRowId()).willReturn(cardId);
            given(cardCatalogRepository.findById(cardId)).willReturn(Optional.of(catalog));
            given(cardCatalogBrandRepository.findAllByCardCatalog(cardId)).willReturn(List.of());
            given(cardCatalogBenefitRepository.findAllByCardCatalog(cardId)).willReturn(List.of());
            given(cardCatalogTagRepository.findAllByCardCatalog(cardId)).willReturn(List.of());

            CardCatalogServiceDto.CatalogDetail detail = sut.getDetail(cardId);

            assertThat(detail.summary().rowId()).isEqualTo(cardId);
            assertThat(detail.brands()).isEmpty();
            assertThat(detail.benefits()).isEmpty();
            assertThat(detail.cautions()).isEmpty();
            assertThat(detail.topBenefits()).isEmpty();
            assertThat(detail.searchBenefits()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 카드 — EntityNotFoundException")
        void throwsWhenNotFound() {
            given(cardCatalogRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getDetail(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
