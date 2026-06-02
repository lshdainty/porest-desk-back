package com.porest.desk.card.domain;

import com.porest.desk.common.domain.CreatedAuditingFieldsWithIp;
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
@Table(name = "card_catalog_annual_fee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalogAnnualFee extends CreatedAuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id", nullable = false)
    private CardCatalog cardCatalog;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "brand", length = 30)
    private String brand;

    @Column(name = "scope", length = 20)
    private String scope;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
