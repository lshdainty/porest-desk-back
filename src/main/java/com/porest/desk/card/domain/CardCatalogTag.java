package com.porest.desk.card.domain;

import com.porest.desk.card.type.CardTagKind;
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
@Table(name = "card_catalog_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalogTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id", nullable = false)
    private CardCatalog cardCatalog;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private CardTagKind kind;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "tag_text", nullable = false, length = 100)
    private String tagText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
