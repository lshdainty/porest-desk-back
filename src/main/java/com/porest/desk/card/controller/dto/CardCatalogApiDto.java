package com.porest.desk.card.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;

import java.time.LocalDate;
import java.util.List;

public class CardCatalogApiDto {

    public record SummaryResponse(
        Long rowId,
        Long externalCardId,
        CompanyResponse company,
        String cardName,
        CardType cardType,
        CardBenefitType benefitType,
        YNType isDiscontinued,
        YNType onlyOnline,
        LocalDate launchDate,
        String imgUrl,
        String detailUrl,
        AnnualFeeResponse annualFee,
        PerformanceResponse performance
    ) {
        public static SummaryResponse from(CardCatalogServiceDto.CatalogSummary s) {
            return new SummaryResponse(
                s.rowId(), s.externalCardId(), CompanyResponse.from(s.company()),
                s.cardName(), s.cardType(), s.benefitType(),
                s.isDiscontinued(), s.onlyOnline(), s.launchDate(),
                s.imgUrl(), s.detailUrl(),
                AnnualFeeResponse.from(s.annualFee()),
                PerformanceResponse.from(s.performance())
            );
        }
    }

    public record CompanyResponse(Long rowId, String name, String nameEng, String logoUrl) {
        public static CompanyResponse from(CardCatalogServiceDto.CompanyInfo c) {
            if (c == null) return null;
            return new CompanyResponse(c.rowId(), c.name(), c.nameEng(), c.logoUrl());
        }
    }

    public record AnnualFeeResponse(Integer amount, String label) {
        public static AnnualFeeResponse from(CardCatalogServiceDto.AnnualFeeInfo a) {
            if (a == null) return null;
            return new AnnualFeeResponse(a.amount(), a.label());
        }
    }

    public record PerformanceResponse(Integer requiredAmount, String requiredText, YNType isRequired) {
        public static PerformanceResponse from(CardCatalogServiceDto.PerformanceInfo p) {
            if (p == null) return null;
            return new PerformanceResponse(p.requiredAmount(), p.requiredText(), p.isRequired());
        }
    }

    public record BenefitResponse(
        Long rowId,
        String category,
        String categoryIcon,
        String title,
        String summary,
        String detail,
        Integer sortOrder
    ) {
        public static BenefitResponse from(CardCatalogServiceDto.BenefitInfo b) {
            return new BenefitResponse(b.rowId(), b.category(), b.categoryIcon(), b.title(), b.summary(), b.detail(), b.sortOrder());
        }
    }

    public record TagGroupResponse(String category, List<String> tags) {
        public static TagGroupResponse from(CardCatalogServiceDto.TagGroup t) {
            return new TagGroupResponse(t.category(), t.tags());
        }
    }

    public record DetailResponse(
        SummaryResponse summary,
        List<String> brands,
        List<BenefitResponse> benefits,
        List<BenefitResponse> cautions,
        List<TagGroupResponse> topBenefits,
        List<TagGroupResponse> searchBenefits
    ) {
        public static DetailResponse from(CardCatalogServiceDto.CatalogDetail d) {
            return new DetailResponse(
                SummaryResponse.from(d.summary()),
                d.brands(),
                d.benefits().stream().map(BenefitResponse::from).toList(),
                d.cautions().stream().map(BenefitResponse::from).toList(),
                d.topBenefits().stream().map(TagGroupResponse::from).toList(),
                d.searchBenefits().stream().map(TagGroupResponse::from).toList()
            );
        }
    }
}
