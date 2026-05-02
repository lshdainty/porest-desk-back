package com.porest.desk.card.domain;

import com.porest.desk.card.type.CardBenefitKind;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_catalog_benefit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalogBenefit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id", nullable = false)
    private CardCatalog cardCatalog;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private CardBenefitKind kind;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "category_icon", length = 500)
    private String categoryIcon;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "summary", length = 300)
    private String summary;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
