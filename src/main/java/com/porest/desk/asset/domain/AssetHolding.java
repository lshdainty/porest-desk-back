package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투자(INVESTMENT) 자산의 보유 종목.
 * linked=Y: 토스 종목코드×수량 — 시세 연동 평가. linked=N: 이름·평가액 수동 입력.
 */
@Entity
@Table(name = "asset_holding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetHolding extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked", nullable = false, length = 1)
    private YNType linked;

    @Column(name = "toss_symbol", length = 30)
    private String tossSymbol;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "holding_name", length = 100)
    private String holdingName;

    @Column(name = "holding_value")
    private Long holdingValue;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetHolding create(Asset asset, YNType linked, String tossSymbol, Long quantity,
                                       String holdingName, Long holdingValue, Integer sortOrder) {
        AssetHolding holding = new AssetHolding();
        holding.asset = asset;
        holding.linked = linked != null ? linked : YNType.N;
        holding.tossSymbol = tossSymbol;
        holding.quantity = quantity;
        holding.holdingName = holdingName;
        holding.holdingValue = holdingValue;
        holding.sortOrder = sortOrder != null ? sortOrder : 0;
        holding.isDeleted = YNType.N;
        return holding;
    }

    public boolean isLinked() {
        return linked == YNType.Y;
    }

    public void deleteHolding() {
        this.isDeleted = YNType.Y;
    }
}
