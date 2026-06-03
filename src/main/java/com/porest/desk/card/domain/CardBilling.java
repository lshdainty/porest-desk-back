package com.porest.desk.card.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.card.type.BillingStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 신용카드 청구(자동결제) 이력. 청구 사이클별 1행.
 * 회계 정합을 위해 실제 결제는 {@link AssetTransfer} 로만 이뤄지며, 본 엔티티는 결과 이력만 보관한다.
 */
@Entity
@Table(name = "card_billing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardBilling extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_asset_row_id", nullable = false)
    private Asset cardAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_asset_row_id")
    private Asset paymentAsset;

    @Column(name = "billing_amount", nullable = false)
    private Long billingAmount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_row_id")
    private AssetTransfer transfer;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    private CardBilling(Asset cardAsset, Asset paymentAsset, Long billingAmount,
                        LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDate,
                        BillingStatus status, AssetTransfer transfer, String failureReason) {
        this.cardAsset = cardAsset;
        this.paymentAsset = paymentAsset;
        this.billingAmount = billingAmount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.paymentDate = paymentDate;
        this.status = status;
        this.transfer = transfer;
        this.failureReason = failureReason;
        this.isDeleted = YNType.N;
    }

    /** 결제 완료(COMPLETED) — 이체 연결. */
    public static CardBilling completed(Asset cardAsset, Asset paymentAsset, Long billingAmount,
                                        LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDate,
                                        AssetTransfer transfer) {
        return new CardBilling(cardAsset, paymentAsset, billingAmount, periodStart, periodEnd, paymentDate,
            BillingStatus.COMPLETED, transfer, null);
    }

    /** 청구액 0 — 건너뜀(SKIPPED). */
    public static CardBilling skipped(Asset cardAsset, Asset paymentAsset,
                                      LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDate) {
        return new CardBilling(cardAsset, paymentAsset, 0L, periodStart, periodEnd, paymentDate,
            BillingStatus.SKIPPED, null, null);
    }

    /** 결제 실패(FAILED) — 사유 기록. */
    public static CardBilling failed(Asset cardAsset, Asset paymentAsset, Long billingAmount,
                                     LocalDate periodStart, LocalDate periodEnd, LocalDate paymentDate,
                                     String failureReason) {
        return new CardBilling(cardAsset, paymentAsset, billingAmount, periodStart, periodEnd, paymentDate,
            BillingStatus.FAILED, null, failureReason);
    }
}
