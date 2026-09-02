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

    /** 다가오는 회차의 할부 한 건 — 명세서의 "원금·N개월 중 k회차" 표시용. */
    public record InstallmentDueResponse(
        Long expenseRowId,
        String merchant,
        String description,
        Long principalAmount,
        Integer installmentMonths,
        Integer sequence,
        Long amount,
        boolean paidOff
    ) {
        public static InstallmentDueResponse from(CardPaymentServiceDto.InstallmentDue d) {
            return new InstallmentDueResponse(
                d.expenseRowId(), d.merchant(), d.description(),
                d.principalAmount(), d.installmentMonths(), d.sequence(), d.amount(),
                d.paidOff()
            );
        }
    }

    public record CardBillingResponse(
        Long cardAssetRowId,
        Long upcomingAmount,
        Long upcomingLumpSumAmount,
        Long upcomingAlreadyPaidAmount,
        List<InstallmentDueResponse> upcomingInstallments,
        LocalDate upcomingPeriodStart,
        LocalDate upcomingPeriodEnd,
        LocalDate nextPaymentDate,
        Integer paymentDay,
        Long paymentAssetRowId,
        List<BillingItemResponse> history,
        UpcomingCycleResponse nextCycle) {
        public static CardBillingResponse from(CardPaymentServiceDto.CardBillingInfo info) {
            return new CardBillingResponse(
                info.cardAssetRowId(),
                info.upcomingAmount(),
                info.upcomingLumpSumAmount(),
                info.upcomingAlreadyPaidAmount(),
                info.upcomingInstallments().stream().map(InstallmentDueResponse::from).toList(),
                info.upcomingPeriodStart(),
                info.upcomingPeriodEnd(),
                info.nextPaymentDate(),
                info.paymentDay(),
                info.paymentAssetRowId(),
                info.history().stream().map(BillingItemResponse::from).toList(),
                info.nextCycle() != null ? UpcomingCycleResponse.from(info.nextCycle()) : null);
        }
    }

    /** 회차 하나 — 청구 응답의 nextCycle(지금 쌓이는 이용분). */
    public record UpcomingCycleResponse(
        java.time.LocalDate paymentDate,
        java.time.LocalDate periodStart,
        java.time.LocalDate periodEnd,
        Long amount,
        Long lumpSumAmount,
        Long alreadyPaidAmount,
        List<InstallmentDueResponse> installments
    ) {
        public static UpcomingCycleResponse from(CardPaymentServiceDto.UpcomingCycle c) {
            return new UpcomingCycleResponse(c.paymentDate(), c.periodStart(), c.periodEnd(),
                c.amount(), c.lumpSumAmount(), c.alreadyPaidAmount(),
                c.installments().stream().map(InstallmentDueResponse::from).toList());
        }
    }
}
