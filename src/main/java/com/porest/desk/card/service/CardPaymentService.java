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

    /** 스케줄러용 — today 가 결제일(말일 보정 포함)인 모든 신용카드 자동결제 처리. */
    void processDueCardPayments(LocalDate today);
}
