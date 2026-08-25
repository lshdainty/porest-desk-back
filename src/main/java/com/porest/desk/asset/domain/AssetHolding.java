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

    /** stock_master 기준 시장코드. symbol 과 짝으로 종목을 특정한다(증권사 표기 아님). */
    @Column(name = "market_code", length = 10)
    private String marketCode;

    @Column(name = "symbol", length = 30)
    private String symbol;

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

    /**
     * @param marketCode stock_master 기준 시장코드. 모르면 null 로 둔다 — 같은 심볼이 여러
     *                   시장에 걸릴 때 아무거나 넣으면 런던 상장분 시세로 미국 보유를 평가한다.
     */
    public static AssetHolding create(Asset asset, HoldingType holdingType, YNType linked,
                                       String marketCode, String symbol,
                                       BigDecimal quantity, String holdingName, Long holdingValue,
                                       Long totalCost, Integer sortOrder) {
        AssetHolding holding = new AssetHolding();
        holding.asset = asset;
        holding.holdingType = holdingType != null ? holdingType : HoldingType.STOCK;
        holding.linked = linked != null ? linked : YNType.N;
        holding.marketCode = marketCode;
        holding.symbol = symbol;
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
        return isLinked() ? symbol : holdingName;
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
     *
     * <p>매수/매도는 costDelta 부호로 알 수 없어 따로 받는다 — 매수의 취소도 음수로
     * 들어와서, 부호로 가르면 매수 취소가 매도 취급되어 되돌린 값이 어긋난다.
     */
    public void applyTrade(BigDecimal quantityDelta, long costDelta, boolean sell) {
        BigDecimal base = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal next = base.add(quantityDelta);
        applyValuationDelta(base, next, costDelta, sell);
        this.quantity = next;
        this.totalCost = Math.max(0L, (totalCost != null ? totalCost : 0L) + costDelta);
    }

    /**
     * 손으로 넣어 둔 평가액을 거래에 맞춰 따라가게 한다.
     *
     * <p>평가액이 없으면(연동 종목이거나 아직 안 넣었으면) 아무것도 안 한다 — 그때는
     * 취득원가나 시세 스냅샷이 평가금액을 대신한다.
     *
     * <p>평가액이 있는데 그대로 두면 <b>순자산이 증발한다.</b> 골드바 평가액 18,000 인
     * 상태에서 6,000 어치를 더 사면 예수금은 6,000 빠지는데 평가액은 18,000 그대로라
     * 6,000 이 사라진다. 돈이 자산 안에서 자리만 옮긴 것이므로 총액은 그대로여야 한다.
     *
     * <ul>
     *   <li>매수: 산 값만큼 늘린다(18,000 + 6,000 = 24,000). 미연동은 시세를 모르니
     *       방금 치른 값이 그 몫의 현재 값이다. 취소는 costDelta 가 음수로 들어와
     *       같은 식으로 빠진다.</li>
     *   <li>매도: 판 비율만큼 줄인다. 원가가 아니라 <b>평가액</b> 기준이라야 남은 몫의
     *       평가가 유지된다. 취소는 before·after 가 뒤집혀 들어와 곱이 저절로
     *       역연산이 된다.</li>
     * </ul>
     *
     * <p>연산과 역연산이 짝을 이뤄야 취소가 원래 값으로 돌아온다 — 매수 취소를 비율로
     * 덜면(평가액 ≠ 원가일 때) 매수·취소 왕복마다 평가액이 새어 순자산이 표류한다.
     */
    private void applyValuationDelta(BigDecimal before, BigDecimal after, long costDelta, boolean sell) {
        if (holdingValue == null) {
            return;
        }
        if (!sell) {
            // 매수 — 치른 값만큼. 취소로 원가보다 아래로 뚫리면 0 에서 멈춘다(totalCost 와 같은 취급).
            this.holdingValue = Math.max(0L, holdingValue + costDelta);
            return;
        }
        // 매도 — 판 비율만큼 덜어낸다. 다 팔면 0.
        if (before.signum() <= 0 || after.signum() <= 0) {
            this.holdingValue = 0L;
            return;
        }
        this.holdingValue = BigDecimal.valueOf(holdingValue)
            .multiply(after)
            .divide(before, 0, java.math.RoundingMode.HALF_UP)
            .longValue();
    }

    /**
     * 편집 폼이 보낸 값으로 제자리 수정 — row_id 가 그대로라 거래(asset_trade) 연결이 끊기지 않는다.
     * 원가는 매수·매도가 쌓은 값이라 안 보내오면(null) 유지한다.
     */
    public void updateHolding(HoldingType holdingType, YNType linked, String marketCode, String symbol,
                              BigDecimal quantity, String holdingName, Long holdingValue,
                              Long totalCost, Integer sortOrder) {
        this.holdingType = holdingType != null ? holdingType : this.holdingType;
        this.linked = linked != null ? linked : this.linked;
        this.marketCode = marketCode;
        this.symbol = symbol;
        this.quantity = quantity;
        this.holdingName = holdingName;
        this.holdingValue = holdingValue;
        if (totalCost != null) {
            this.totalCost = totalCost;
        }
        this.sortOrder = sortOrder != null ? sortOrder : this.sortOrder;
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
