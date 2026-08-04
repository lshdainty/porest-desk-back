package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;
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
import java.time.LocalDateTime;

/**
 * 투자 자산의 매수·매도 거래.
 *
 * <p>증권계좌에서 예수금이 줄고 느는 진짜 사건은 매수·매도다. 그게 없으면 평가액 갱신을 보고
 * 예수금을 추측할 수밖에 없는데, 시세 변동·추가 매수·재등록이 전부 같은 갱신으로 들어와
 * 구분이 안 된다. 거래로 남기면 예수금이 실제 흐름대로 움직인다.
 *
 * <pre>
 *   매수  예수금 -(대금 + 수수료),  수량 +, 원가 +(대금 + 수수료)
 *   매도  예수금 +(대금 - 수수료),  수량 -, 원가 -(판 만큼의 원가)
 *         실현손익 = (대금 - 수수료) - 판 만큼의 원가
 * </pre>
 *
 * <p>원가는 이동평균이다. 매수 수수료는 취득원가에 넣고, 매도 수수료는 대금에서 뺀다.
 *
 * <p>{@code quantityDelta}·{@code costDelta} 를 따로 남기는 이유 — 이동평균은 순서에 의존해서
 * 거래를 취소할 때 "그때 원가가 얼마나 빠졌는지" 를 현재 기준으로 다시 계산하면 어긋난다.
 * 그 시점의 변동분을 박아 두면 역적용으로 정확히 되돌아간다.
 *
 * <p>보유({@link AssetHolding})와는 {@code holdingKey} 로 묶는다. 보유 목록은 편집할 때마다
 * 통째로 재생성돼서(soft delete 후 재삽입) row_id 로는 묶을 수 없다.
 */
@Entity
@Table(name = "asset_trade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetTrade extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id", nullable = false)
    private com.porest.desk.user.domain.User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id", nullable = false)
    private Asset asset;

    /**
     * 결제 계좌 — 지정하면 증권계좌 예수금 대신 이 계좌에서 돈이 오간다.
     * 예수금을 따로 관리하지 않는 사용자를 위한 길이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_asset_row_id")
    private Asset settlementAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 10)
    private TradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "holding_type", nullable = false, length = 10)
    private HoldingType holdingType;

    /** 종목 식별자 — 연동은 토스 종목코드, 미연동은 항목명. */
    @Column(name = "holding_key", nullable = false, length = 100)
    private String holdingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked", nullable = false, length = 1)
    private YNType linked;

    /** 거래 수량 — 항상 양수. 방향은 tradeType 이 정한다. */
    @Column(name = "quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    /** 거래대금 (원화, 수수료 제외). */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "fee", nullable = false)
    private Long fee;

    @Column(name = "quantity_delta", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantityDelta;

    @Column(name = "cost_delta", nullable = false)
    private Long costDelta;

    /** 실현손익 (매도 전용). 이익 양수 / 손실 음수. */
    @Column(name = "realized_pl")
    private Long realizedPl;

    /** 실현손익을 기록한 거래(expense) 행 아이디 — 매도 취소 시 함께 지운다. */
    @Column(name = "realized_expense_row_id")
    private Long realizedExpenseRowId;

    @Column(name = "trade_date", nullable = false)
    private LocalDateTime tradeDate;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetTrade create(com.porest.desk.user.domain.User user, Asset asset,
                                    Asset settlementAsset,
                                    TradeType tradeType, HoldingType holdingType, String holdingKey,
                                    YNType linked, BigDecimal quantity, Long amount, Long fee,
                                    BigDecimal quantityDelta, Long costDelta,
                                    LocalDateTime tradeDate, String description) {
        AssetTrade t = new AssetTrade();
        t.user = user;
        t.asset = asset;
        t.settlementAsset = settlementAsset;
        t.tradeType = tradeType;
        t.holdingType = holdingType != null ? holdingType : HoldingType.STOCK;
        t.holdingKey = holdingKey;
        t.linked = linked != null ? linked : YNType.N;
        t.quantity = quantity;
        t.amount = amount != null ? amount : 0L;
        t.fee = fee != null ? fee : 0L;
        t.quantityDelta = quantityDelta;
        t.costDelta = costDelta;
        t.tradeDate = tradeDate;
        t.description = description;
        t.isDeleted = YNType.N;
        return t;
    }

    public void recordRealized(long realizedPl, Long realizedExpenseRowId) {
        this.realizedPl = realizedPl;
        this.realizedExpenseRowId = realizedExpenseRowId;
    }

    public void deleteTrade() {
        this.isDeleted = YNType.Y;
    }

    /**
     * 예수금 변동액 — 매수는 대금과 수수료가 함께 빠지고, 매도는 수수료를 뗀 나머지가 들어온다.
     * 기초 보유(OPENING)는 앱을 쓰기 전 일이라 돈이 오가지 않는다.
     */
    /** 돈이 실제로 오가는 자산 — 결제 계좌를 골랐으면 거기, 아니면 증권계좌 예수금. */
    public Asset cashAsset() {
        return settlementAsset != null ? settlementAsset : asset;
    }

    public long cashDelta() {
        return switch (tradeType) {
            case BUY -> -(amount + fee);
            case SELL -> amount - fee;
            case OPENING -> 0L;
        };
    }
}
