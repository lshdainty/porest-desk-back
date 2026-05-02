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
        public static AnnualFeeInfo from(CardCatalog c) {
            return new AnnualFeeInfo(c.getAnnualFeeAmount(), c.getAnnualFeeLabel());
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
