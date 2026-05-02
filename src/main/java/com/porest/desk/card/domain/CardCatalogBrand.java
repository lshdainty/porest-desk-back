package com.porest.desk.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "card_catalog_brand")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalogBrand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id", nullable = false)
    private CardCatalog cardCatalog;

    @Column(name = "brand", nullable = false, length = 30)
    private String brand;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
