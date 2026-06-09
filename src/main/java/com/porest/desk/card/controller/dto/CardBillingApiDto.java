package com.porest.desk.card.controller.dto;

import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CardBillingApiDto {

    public record BillingItemResponse(
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
        public static BillingItemResponse from(CardPaymentServiceDto.BillingInfo b) {
            return new BillingItemResponse(
                b.rowId(), b.cardAssetRowId(), b.paymentAssetRowId(), b.billingAmount(),
                b.periodStart(), b.periodEnd(), b.paymentDate(), b.status(),
                b.transferRowId(), b.failureReason(), b.createAt()
            );
        }
    }

    public record CardBillingResponse(
        Long cardAssetRowId,
        Long upcomingAmount,
        LocalDate nextPaymentDate,
        Integer paymentDay,
        Long paymentAssetRowId,
        List<BillingItemResponse> history
    ) {
        public static CardBillingResponse from(CardPaymentServiceDto.CardBillingInfo info) {
            return new CardBillingResponse(
                info.cardAssetRowId(),
                info.upcomingAmount(),
                info.nextPaymentDate(),
                info.paymentDay(),
                info.paymentAssetRowId(),
                info.history().stream().map(BillingItemResponse::from).toList()
            );
        }
    }
}
