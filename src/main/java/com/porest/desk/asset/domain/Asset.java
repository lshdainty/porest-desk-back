package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
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
@Table(name = "asset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id")
    private CardCatalog cardCatalog;

    @Column(name = "asset_name", nullable = false, length = 100)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "institution", length = 100)
    private String institution;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_included_in_total", nullable = false, length = 1)
    private YNType isIncludedInTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Asset createAsset(User user, String assetName, AssetType assetType, Long balance,
                                     String currency, String icon, String color, String institution,
                                     String memo, Integer sortOrder, CardCatalog cardCatalog) {
        Asset asset = new Asset();
        asset.user = user;
        asset.cardCatalog = cardCatalog;
        asset.assetName = assetName;
        asset.assetType = assetType;
        asset.balance = balance;
        asset.currency = currency;
        asset.icon = icon;
        asset.color = color;
        asset.institution = institution;
        asset.memo = memo;
        asset.sortOrder = sortOrder;
        asset.isIncludedInTotal = YNType.Y;
        asset.isDeleted = YNType.N;
        return asset;
    }

    public void updateAsset(String assetName, AssetType assetType, Long balance, String currency,
                            String icon, String color, String institution, String memo,
                            YNType isIncludedInTotal, CardCatalog cardCatalog) {
        this.assetName = assetName;
        this.assetType = assetType;
        this.balance = balance;
        this.currency = currency;
        this.icon = icon;
        this.color = color;
        this.institution = institution;
        this.memo = memo;
        this.isIncludedInTotal = isIncludedInTotal != null ? isIncludedInTotal : this.isIncludedInTotal;
        this.cardCatalog = cardCatalog;
    }

    public void updateBalance(Long balance) {
        this.balance = balance;
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deleteAsset() {
        this.isDeleted = YNType.Y;
    }
}
