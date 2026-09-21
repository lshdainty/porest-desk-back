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

    /**
     * 회차의 할부 한 건 — 명세서의 "원금·N개월 중 k회차" 표시용(다가오는·다음·닫힌 회차 공통).
     *
     * @param recordOnly 기록용 회차분 — 결제가 끝난 회차에 뒤늦게 적어 계좌에서 안 빠졌다("· 기록만")
     */
    public record InstallmentDueResponse(
        Long expenseRowId,
        String merchant,
        String description,
        Long principalAmount,
        Integer installmentMonths,
        Integer sequence,
        Long amount,
        boolean paidOff,
        boolean recordOnly
    ) {
        public static InstallmentDueResponse from(CardPaymentServiceDto.InstallmentDue d) {
            return new InstallmentDueResponse(
                d.expenseRowId(), d.merchant(), d.description(),
                d.principalAmount(), d.installmentMonths(), d.sequence(), d.amount(),
                d.paidOff(), d.recordOnly()
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
        UpcomingCycleResponse nextCycle,
        /** 닫힌 회차(결제일이 지난) — 회차 선택기의 과거 칸. 최신 회차부터. */
        List<ClosedCycleResponse> closedCycles) {
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
                info.nextCycle() != null ? UpcomingCycleResponse.from(info.nextCycle()) : null,
                info.closedCycles().stream().map(ClosedCycleResponse::from).toList());
        }
    }

    /**
     * 닫힌 회차 하나 — 명세서 머리 금액은 {@code recordedAmount}(지금 기록 합, D10)다.
     *
     * @param paymentDate        그 회차에 실제로 적용된 결제일(D5 — 결제일을 바꿨어도 그 회차 것)
     * @param paidAmount         결제로 정리된 순 금액(결제 − 환급)
     * @param recordedAmount     그 회차의 지금 기록 합 — 일시불 지출 − 카드 수입 + 할부 회차분(환불 제외)
     * @param recordedOnlyAmount 하위 호환 — {@code max(0, recorded − paid)}
     * @param preRegistration    카드 등록 전 회차 — "실제와 맞지 않을 수 있어요" 주의 문구용
     * @param refundableUntil    하위 호환 — 이제 항상 null(닫힌 회차는 돌려주지 않는다, D1)
     * @param installmentDues    그 회차의 할부 회차분 구성(24차 8)
     */
    public record ClosedCycleResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        long paidAmount,
        long recordedAmount,
        long recordedOnlyAmount,
        boolean preRegistration,
        LocalDate refundableUntil,
        List<InstallmentDueResponse> installmentDues
    ) {
        public static ClosedCycleResponse from(CardPaymentServiceDto.ClosedCycle c) {
            return new ClosedCycleResponse(c.periodStart(), c.periodEnd(), c.paymentDate(),
                c.paidAmount(), c.recordedAmount(), c.recordedOnlyAmount(), c.preRegistration(),
                c.refundableUntil(),
                c.installmentDues().stream().map(InstallmentDueResponse::from).toList());
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
                c.amount(), c.lumpSumAmount(), c.alreadyPaidAmount(), c.installments().stream().map(InstallmentDueResponse::from).toList());
        }
    }
}
