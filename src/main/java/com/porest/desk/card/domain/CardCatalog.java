package com.porest.desk.card.domain;

import com.porest.core.type.YNType;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "card_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalog extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "external_card_id", nullable = false, unique = true)
    private Long externalCardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_company_row_id", nullable = false)
    private CardCompany company;

    @Column(name = "card_name", nullable = false, length = 100)
    private String cardName;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 10)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 20)
    private CardBenefitType benefitType;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_discontinued", nullable = false, length = 1)
    private YNType isDiscontinued;

    @Enumerated(EnumType.STRING)
    @Column(name = "only_online", nullable = false, length = 1)
    private YNType onlyOnline;

    @Column(name = "launch_date")
    private LocalDate launchDate;

    @Column(name = "img_url", length = 500)
    private String imgUrl;

    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    @Column(name = "annual_fee_amount", nullable = false)
    private Integer annualFeeAmount;

    @Column(name = "annual_fee_label", length = 500)
    private String annualFeeLabel;

    @Column(name = "performance_required_amount", nullable = false)
    private Integer performanceRequiredAmount;

    @Column(name = "performance_required_text", length = 100)
    private String performanceRequiredText;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_is_required", nullable = false, length = 1)
    private YNType performanceIsRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    @OneToMany(mappedBy = "cardCatalog", fetch = FetchType.LAZY)
    private List<CardCatalogBrand> brands = new ArrayList<>();

    @OneToMany(mappedBy = "cardCatalog", fetch = FetchType.LAZY)
    private List<CardCatalogBenefit> benefits = new ArrayList<>();

    @OneToMany(mappedBy = "cardCatalog", fetch = FetchType.LAZY)
    private List<CardCatalogTag> tags = new ArrayList<>();
}
