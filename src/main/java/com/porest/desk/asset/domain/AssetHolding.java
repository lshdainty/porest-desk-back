package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.type.HoldingType;
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

import java.math.BigDecimal;

/**
 * 투자(INVESTMENT) 자산의 보유 종목.
 * linked=Y: 토스 종목코드×수량 — 시세 연동 평가. linked=N: 이름·평가액 수동 입력(수량은 선택).
 *
 * <p>유형(holdingType)마다 수량 단위가 다르다 — 주식 주 / 금 g / 코인 개.
 * 그래서 수량은 소수를 담는다(코인 0.05 BTC·금 3.75g).
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
    @Column(name = "holding_type", nullable = false, length = 10)
    private HoldingType holdingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked", nullable = false, length = 1)
    private YNType linked;

    @Column(name = "toss_symbol", length = 30)
    private String tossSymbol;

    @Column(name = "quantity", precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "holding_name", length = 100)
    private String holdingName;

    @Column(name = "holding_value")
    private Long holdingValue;

    /** 총 매수원가 (원화, 수수료 포함). 평단가는 {@link #avgPrice()} 로 파생한다. */
    @Column(name = "total_cost", nullable = false)
    private Long totalCost;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetHolding create(Asset asset, HoldingType holdingType, YNType linked, String tossSymbol,
                                       BigDecimal quantity, String holdingName, Long holdingValue,
                                       Long totalCost, Integer sortOrder) {
        AssetHolding holding = new AssetHolding();
        holding.asset = asset;
        holding.holdingType = holdingType != null ? holdingType : HoldingType.STOCK;
        holding.linked = linked != null ? linked : YNType.N;
        holding.tossSymbol = tossSymbol;
        holding.quantity = quantity;
        holding.holdingName = holdingName;
        holding.holdingValue = holdingValue;
        holding.totalCost = totalCost != null ? totalCost : 0L;
        holding.sortOrder = sortOrder != null ? sortOrder : 0;
        holding.isDeleted = YNType.N;
        return holding;
    }

    public boolean isLinked() {
        return linked == YNType.Y;
    }

    /**
     * 종목 식별자 — 거래({@link AssetTrade})와 보유를 묶는 키.
     * 보유 목록은 편집할 때마다 통째로 재생성돼서 row_id 로는 묶을 수 없다.
     */
    public String holdingKey() {
        return isLinked() ? tossSymbol : holdingName;
    }

    /** 평단가 — 총원가 / 수량. 수량이 없으면 없다. 평단가를 직접 들면 부분 매도마다 오차가 쌓인다. */
    public BigDecimal avgPrice() {
        if (quantity == null || quantity.signum() == 0 || totalCost == null) {
            return null;
        }
        return BigDecimal.valueOf(totalCost).divide(quantity, 8, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 거래 한 건을 반영한다 — 수량과 원가가 함께 움직인다.
     * 취소는 부호를 뒤집어 같은 메서드로 되돌린다.
     */
    public void applyTrade(BigDecimal quantityDelta, long costDelta) {
        BigDecimal base = quantity != null ? quantity : BigDecimal.ZERO;
        this.quantity = base.add(quantityDelta);
        this.totalCost = Math.max(0L, (totalCost != null ? totalCost : 0L) + costDelta);
    }

    /** 사용자가 보유를 직접 고칠 때 — 매수/매도를 거치지 않는 보정 경로. */
    public void adjust(BigDecimal quantity, Long holdingValue, Long totalCost) {
        this.quantity = quantity;
        this.holdingValue = holdingValue;
        this.totalCost = totalCost != null ? totalCost : this.totalCost;
    }

    public void deleteHolding() {
        this.isDeleted = YNType.Y;
    }
}
