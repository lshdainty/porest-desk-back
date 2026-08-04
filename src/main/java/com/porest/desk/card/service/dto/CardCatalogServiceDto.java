package com.porest.desk.card.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.domain.CardCatalogBenefit;
import com.porest.desk.card.domain.CardCatalogBrand;
import com.porest.desk.card.domain.CardCatalogTag;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardTagKind;
import com.porest.desk.card.type.CardType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CardCatalogServiceDto {

    public record CompanyInfo(
        Long rowId,
        String name,
        String nameEng,
        String logoUrl
    ) {
        public static CompanyInfo from(com.porest.desk.card.domain.CardCompany company) {
            if (company == null) return null;
            return new CompanyInfo(company.getRowId(), company.getName(), company.getNameEng(), company.getLogoUrl());
        }
    }

    public record AnnualFeeInfo(Integer amount, String label) {
        /**
         * 금액이 0 이고 라벨도 없으면 "연회비가 0원인 카드"가 아니라 "연회비 정보가 없는 카드"다.
         * annualFeeAmount 는 NOT NULL DEFAULT 0 이라 미수집분도 0 으로 내려가서,
         * 화면이 둘을 구분하지 못하고 똑같이 "없음"으로 찍혀 무료 카드처럼 보였다.
         * (2026-08 실측: 9,466 장 중 5,399 장이 이 상태 — 카드사 공시 PDF 는 연회비를 안 준다)
         *
         * 그래서 정보가 없으면 null 을 내린다. 클라이언트는
         *   null → "연회비 정보 없음" / amount>0 → 금액 / 그 외 → "연회비 무료"
         * 로 표시한다.
         *
         * 자식 테이블(card_catalog_annual_fee)은 보지 않는다. 목록 조회에서 카드마다
         * LAZY 로딩이 돌면 N+1 이 되고, 실측상 "자식 행은 있는데 본문만 빈" 카드는 0 장이라
         * 본문 컬럼만으로 같은 결론이 나온다.
         */
        public static AnnualFeeInfo from(CardCatalog c) {
            Integer amount = c.getAnnualFeeAmount();
            String label = c.getAnnualFeeLabel();
            boolean noAmount = (amount == null || amount == 0);
            boolean noLabel = (label == null || label.isBlank());
            if (noAmount && noLabel) return null;
            return new AnnualFeeInfo(amount, label);
        }
    }

    public record PerformanceInfo(Integer requiredAmount, String requiredText, YNType isRequired) {
        public static PerformanceInfo from(CardCatalog c) {
            return new PerformanceInfo(c.getPerformanceRequiredAmount(), c.getPerformanceRequiredText(), c.getPerformanceIsRequired());
        }
    }

    public record CatalogSummary(
        Long rowId,
        Long externalCardId,
        CompanyInfo company,
        String cardName,
        CardType cardType,
        CardBenefitType benefitType,
        YNType isDiscontinued,
        YNType onlyOnline,
        LocalDate launchDate,
        String imgUrl,
        String detailUrl,
        AnnualFeeInfo annualFee,
        PerformanceInfo performance
    ) {
        public static CatalogSummary from(CardCatalog c) {
            return new CatalogSummary(
                c.getRowId(),
                c.getExternalCardId(),
                CompanyInfo.from(c.getCompany()),
                c.getCardName(),
                c.getCardType(),
                c.getBenefitType(),
                c.getIsDiscontinued(),
                c.getOnlyOnline(),
                c.getLaunchDate(),
                c.getImgUrl(),
                c.getDetailUrl(),
                AnnualFeeInfo.from(c),
                PerformanceInfo.from(c)
            );
        }
    }

    public record BenefitInfo(
        Long rowId,
        String category,
        String categoryIcon,
        String title,
        String summary,
        String detail,
        Integer sortOrder
    ) {
        public static BenefitInfo from(CardCatalogBenefit b) {
            return new BenefitInfo(b.getRowId(), b.getCategory(), b.getCategoryIcon(), b.getTitle(), b.getSummary(), b.getDetail(), b.getSortOrder());
        }
    }

    public record TagGroup(String category, List<String> tags) {}

    public record CatalogDetail(
        CatalogSummary summary,
        List<String> brands,
        List<BenefitInfo> benefits,
        List<BenefitInfo> cautions,
        List<TagGroup> topBenefits,
        List<TagGroup> searchBenefits
    ) {
        public static CatalogDetail of(
            CardCatalog catalog,
            List<CardCatalogBrand> brandEntities,
            List<CardCatalogBenefit> benefitEntities,
            List<CardCatalogBenefit> cautionEntities,
            List<CardCatalogTag> topTagEntities,
            List<CardCatalogTag> searchTagEntities
        ) {
            List<String> brands = brandEntities.stream().map(CardCatalogBrand::getBrand).toList();
            List<BenefitInfo> benefits = benefitEntities.stream().map(BenefitInfo::from).toList();
            List<BenefitInfo> cautions = cautionEntities.stream().map(BenefitInfo::from).toList();
            return new CatalogDetail(
                CatalogSummary.from(catalog),
                brands,
                benefits,
                cautions,
                groupTags(topTagEntities),
                groupTags(searchTagEntities)
            );
        }

        private static List<TagGroup> groupTags(List<CardCatalogTag> tags) {
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            for (CardCatalogTag t : tags) {
                grouped.computeIfAbsent(t.getCategory(), k -> new ArrayList<>()).add(t.getTagText());
            }
            return grouped.entrySet().stream()
                .map(e -> new TagGroup(e.getKey(), e.getValue()))
                .toList();
        }
    }

    public record CatalogDetailParts(
        CardCatalog catalog,
        List<CardCatalogBrand> brands,
        List<CardCatalogBenefit> allBenefits,
        List<CardCatalogTag> allTags
    ) {
        public List<CardCatalogBenefit> benefits() {
            return allBenefits.stream().filter(b -> b.getKind() == com.porest.desk.card.type.CardBenefitKind.BENEFIT).toList();
        }

        public List<CardCatalogBenefit> cautions() {
            return allBenefits.stream().filter(b -> b.getKind() == com.porest.desk.card.type.CardBenefitKind.CAUTION).toList();
        }

        public List<CardCatalogTag> topTags() {
            return allTags.stream().filter(t -> t.getKind() == CardTagKind.TOP).toList();
        }

        public List<CardCatalogTag> searchTags() {
            return allTags.stream().filter(t -> t.getKind() == CardTagKind.SEARCH).toList();
        }
    }
}
