package com.porest.desk.card.service;

import com.porest.desk.card.service.dto.CardPaymentServiceDto;

import java.time.LocalDate;

public interface CardPaymentService {
    /** 카드 청구 화면용 — 현재 사이클 예정액 + 다음 결제예정일 + 과거 이력. */
    CardPaymentServiceDto.CardBillingInfo getCardBilling(Long cardRowId, Long userRowId);

    /** 수동 결제 — 결제계좌에서 카드로 이체하여 카드 잔액을 0으로 복귀시키고 COMPLETED 이력 기록. */
    /**
     * 카드 수동 결제.
     *
     * @param amount 결제 금액. null 이면 남은 청구액 전액(종전 동작), 값이 있으면 그만큼만
     *               결제한다(부분 선결제). 남은 청구액은 다음 결제일에 정상적으로 빠진다.
     */
    CardPaymentServiceDto.BillingInfo payCard(Long cardRowId, Long userRowId, Long amount);

    /**
     * 회차를 골라 결제한다 — paymentDate 는 다가오는 회차 또는 그 다음 회차(지금 쌓이는 이용분)의
     * 결제일. null 이면 다가오는 회차(위 오버로드와 같다).
     */
    CardPaymentServiceDto.BillingInfo payCard(Long cardRowId, Long userRowId, Long amount, LocalDate paymentDate);

    /**
     * 할부 중도 전액 상환 — 남은 원금을 <b>다가오는 청구 회차</b>에 몰아 청구되게 한다.
     * 그 회차를 지금 결제로 정리하면 끝난다.
     */
    void payoffInstallment(Long cardRowId, Long expenseRowId, Long userRowId);

    /** 상환 취소 — 정상 분할로 되돌린다. */
    void cancelInstallmentPayoff(Long cardRowId, Long expenseRowId, Long userRowId);

    /**
     * 카드 결제 취소 — 결제로 만들어진 이체를 무르고 청구 회차를 되돌린다.
     *
     * <p>잘못 누른 결제를 되돌릴 길이 없었다. 이체는 CARD_PAYMENT 로 잠겨 있어 사용자가
     * 지울 수 없고(그래야 청구와 따로 놀지 않는다), 취소 API 도 없어 영구적이었다.
     */
    void cancelPayment(Long billingRowId, Long userRowId);

    /** 스케줄러용 — today 가 결제일(말일 보정 포함)인 모든 신용카드 자동결제 처리. */
    void processDueCardPayments(LocalDate today);
}
