package com.porest.desk.card.service.dto;

import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.type.BillingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CardPaymentServiceDto {

    /** 단일 청구 이력 행. */
    public record BillingInfo(
        Long rowId,
        Long cardAssetRowId,
        Long paymentAssetRowId,
        Long billingAmount,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        BillingStatus status,
        Long transferRowId,
        String failureReason,
        LocalDateTime createAt
    ) {
        public static BillingInfo from(CardBilling b) {
            return new BillingInfo(
                b.getRowId(),
                b.getCardAsset() != null ? b.getCardAsset().getRowId() : null,
                b.getPaymentAsset() != null ? b.getPaymentAsset().getRowId() : null,
                b.getBillingAmount(),
                b.getPeriodStart(),
                b.getPeriodEnd(),
                b.getPaymentDate(),
                b.getStatus(),
                b.getTransfer() != null ? b.getTransfer().getRowId() : null,
                b.getFailureReason(),
                b.getCreateAt()
            );
        }
    }

    /**
     * 카드 청구 화면용 종합 응답.
     * - upcomingAmount: 현재 사이클 결제예정액 = abs(card.balance)
     * - nextPaymentDate: payment_day 기준 다음 결제예정일(말일 보정)
     * - paymentAssetRowId: 지정된 결제 출금계좌(없으면 null)
     * - history: 과거 청구 이력(최신순)
     */
    public record CardBillingInfo(
        Long cardAssetRowId,
        Long upcomingAmount,
        LocalDate nextPaymentDate,
        Integer paymentDay,
        Long paymentAssetRowId,
        List<BillingInfo> history
    ) {}
}
