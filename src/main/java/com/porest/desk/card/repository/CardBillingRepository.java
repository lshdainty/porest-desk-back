package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.type.BillingStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardBillingRepository {
    CardBilling save(CardBilling billing);

    /** 카드별 청구 이력 (결제예정일 desc, 최신순). */
    List<CardBilling> findByCardAssetRowId(Long cardAssetRowId);

    /** 멱등성 체크 — 특정 카드의 특정 결제일에 COMPLETED 청구가 이미 존재하는지. */
    boolean existsCompletedByCardAndPaymentDate(Long cardAssetRowId, LocalDate paymentDate);

    /** 특정 청구 회차(기간)에 이미 결제 완료된 금액 합 — 선결제 차감 계산용. */
    long sumCompletedAmountByCardAndPeriod(Long cardAssetRowId, LocalDate periodStart, LocalDate periodEnd);

    /** 상태별 조회. */
    List<CardBilling> findByStatus(BillingStatus status);

    /** 정산 이체로 청구 회차 찾기 — 이체가 지워질 때 함께 무르기 위해. */
    Optional<CardBilling> findActiveByTransfer(Long transferRowId);

    /** 결제 취소 대상 회차 조회. */
    Optional<CardBilling> findById(Long rowId);
}
