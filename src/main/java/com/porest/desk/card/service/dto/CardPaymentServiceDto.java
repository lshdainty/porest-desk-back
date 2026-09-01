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
     * 다가오는 회차에 빠지는 할부 한 건.
     *
     * @param expenseRowId      원거래(지출) 행 아이디
     * @param merchant          가맹점 — 명세서 행의 이름
     * @param description       메모(가맹점이 없을 때의 폴백 표시용)
     * @param principalAmount   할부 원금(거래 전액)
     * @param installmentMonths 총 회차 수(N)
     * @param sequence          이번이 몇 회차인지(1-base)
     * @param amount            이번 회차에 빠지는 금액. 나머지는 1회차에 몰린다(카드사 관행)
     * @param paidOff           이 회차가 중도 전액 상환으로 남은 원금을 몰아 받은 회차인지 —
     *                          화면이 "남은 원금 정리" 표시를 달고 정리 버튼을 숨긴다
     */
    public record InstallmentDue(
        Long expenseRowId,
        String merchant,
        String description,
        Long principalAmount,
        Integer installmentMonths,
        Integer sequence,
        Long amount,
        boolean paidOff
    ) {}

    /**
     * 카드 청구 화면용 종합 응답.
     * - upcomingAmount: 다가오는 결제 회차의 결제예정액
     *   = 청구 기간(결제일의 전월 1일~말일) 순사용액 − 같은 회차 기결제액(선결제 차감).
     *   결제일 미설정 시에만 잔액 전액 fallback(기간 null).
     * - upcomingLumpSumAmount: 회차 내 일시불 순사용액(환불 상계, 음수 가능)
     * - upcomingAlreadyPaidAmount: 같은 회차 기결제액(선결제 차감분)
     * - upcomingInstallments: 이 회차에 빠지는 할부 구성 — 명세서가 원금·회차를 그린다
     * - upcomingPeriodStart/End: 다가오는 회차의 청구 기간(전월 1일~말일)
     * - nextPaymentDate: payment_day 기준 다음 결제예정일(말일 보정)
     * - paymentAssetRowId: 지정된 결제 출금계좌(없으면 null)
     * - history: 과거 청구 이력(최신순)
     */
    public record CardBillingInfo(
        Long cardAssetRowId,
        Long upcomingAmount,
        Long upcomingLumpSumAmount,
        Long upcomingAlreadyPaidAmount,
        List<InstallmentDue> upcomingInstallments,
        LocalDate upcomingPeriodStart,
        LocalDate upcomingPeriodEnd,
        LocalDate nextPaymentDate,
        Integer paymentDay,
        Long paymentAssetRowId,
        List<BillingInfo> history
    ) {}
}
