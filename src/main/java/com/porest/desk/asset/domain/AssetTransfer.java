package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
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

import java.time.LocalDate;

@Entity
@Table(name = "asset_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetTransfer extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_asset_row_id")
    private Asset fromAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_asset_row_id")
    private Asset toAsset;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "fee", nullable = false)
    private Long fee;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetTransfer createTransfer(User user, Asset fromAsset, Asset toAsset,
                                                Long amount, Long fee, String description,
                                                LocalDate transferDate) {
        AssetTransfer transfer = new AssetTransfer();
        transfer.user = user;
        transfer.fromAsset = fromAsset;
        transfer.toAsset = toAsset;
        transfer.amount = amount;
        transfer.fee = fee != null ? fee : 0L;
        transfer.description = description;
        transfer.transferDate = transferDate;
        transfer.isDeleted = YNType.N;
        return transfer;
    }

    public void deleteTransfer() {
        this.isDeleted = YNType.Y;
    }
}
