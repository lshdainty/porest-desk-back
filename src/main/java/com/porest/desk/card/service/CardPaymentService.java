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

    /**
     * 이미 낸 돈이 지금 청구보다 많으면 그만큼 결제계좌로 돌려준다.
     *
     * <p>환불 마크·삭제·감액으로 <b>결제 완료 회차의 카드 거래가 줄어들었을 때</b> 부른다.
     * 회차마다 {@code 크레딧 = 실제 낸 이체액 합 − 지금 다시 계산한 회차 청구액} 을 재고
     * 양수만 더한 뒤, 이미 나간 환급을 빼고 {@code cap}(환불·감액된 금액)까지만 돌려준다.
     *
     * <p>"COMPLETED 가 있으면 전액" 으로 하면 <b>부분 선결제 회차와 할부에서 과다 환급</b>
     * 된다 — 남은 청구가 있는데도 낸 돈 전부를 돌려주게 된다. 잔액 부호로 판정하는 것도
     * 틀린다: 뒤에 쌓인 지출 때문에 잔액이 여전히 음수일 수 있다(네이버 현대카드 실사례).
     *
     * @param cap 돌려줄 상한 — 환불액 또는 줄어든 금액
     * @return 만든 환급 이체와 금액. 돌려줄 크레딧이 없거나 결제계좌가 없으면 {@code null}
     */
    CardPaymentServiceDto.RefundResult refundCreditIfOverpaid(
        Long cardRowId, long cap, String memo, java.time.LocalDateTime at, Long userRowId);

    /**
     * 같은 크레딧 식으로 <b>미리</b> 센다 — 삭제·감액 확인창이 금액을 보여 줄 수 있게(설계 13-1).
     *
     * <p>DB 를 바꾸지 않는다. {@code change} 가 말하는 "바뀐 뒤 모습" 으로 회차 청구액을
     * 다시 세고, {@link #refundCreditIfOverpaid} 와 <b>같은 함수</b>를 인자만 달리해 부른다 —
     * 산식을 복사해 두면 확인창 금액과 실제 이체액이 갈린다.
     *
     * @param cap 돌려줄 상한 — 삭제면 거래 금액, 감액이면 줄어든 금액
     */
    CardPaymentServiceDto.RefundPreview previewRefundCredit(
        Long cardRowId, long cap, CardPaymentServiceDto.ExpenseChange change,
        java.time.LocalDateTime at);
}
