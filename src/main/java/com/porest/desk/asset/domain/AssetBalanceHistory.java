package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.type.BalanceSourceType;
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

import java.time.LocalDateTime;

/**
 * 자산 잔액 변동 이력 (단일 진실 공급원).
 *
 * <p>잔액이 바뀌는 모든 사건을 datetime 스냅샷 row 로 적재한다. "기준시각의 자산 잔액" 은
 * {@code (기준시각 이하 최신 absolute 앵커).amount + 그 이후 flow row 들의 amount 합} 으로 계산하며,
 * 현재 총자산·과거 시점·추이 그래프가 모두 이 동일 메커니즘을 쓰므로 항상 일치한다.
 *
 * @see com.porest.desk.asset.type.BalanceSourceType
 */
@Entity
@Table(name = "asset_balance_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetBalanceHistory extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private BalanceSourceType sourceType;

    /** 출처 엔티티의 row_id (EXPENSE=expense.row_id, TRANSFER=asset_transfer.row_id, INIT/MANUAL/VALUATION=asset.row_id). */
    @Column(name = "source_row_id")
    private Long sourceRowId;

    /** flow = 부호 있는 변동액 / absolute(INIT·MANUAL·VALUATION) = 그 시점 절대 잔액. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetBalanceHistory of(User user, Asset asset, BalanceSourceType sourceType,
                                         Long sourceRowId, Long amount, LocalDateTime effectiveAt) {
        AssetBalanceHistory h = new AssetBalanceHistory();
        h.user = user;
        h.asset = asset;
        h.sourceType = sourceType;
        h.sourceRowId = sourceRowId;
        h.amount = amount;
        h.effectiveAt = effectiveAt;
        h.isDeleted = YNType.N;
        return h;
    }

    public void softDelete() {
        this.isDeleted = YNType.Y;
    }

    /** 절대 앵커(INIT/MANUAL/VALUATION)면 true. */
    public boolean isAbsolute() {
        return sourceType.isAbsolute();
    }
}
