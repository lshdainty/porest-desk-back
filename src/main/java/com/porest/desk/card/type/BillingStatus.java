package com.porest.desk.card.type;

/**
 * 신용카드 청구(자동결제) 상태.
 * <ul>
 *   <li>PENDING   - 결제 예정(아직 미처리)</li>
 *   <li>COMPLETED - 결제 완료(이체 생성됨)</li>
 *   <li>FAILED    - 결제 실패(잔액 부족/결제계좌 미지정 등)</li>
 *   <li>SKIPPED   - 결제할 금액 없음(청구액 0)</li>
 * </ul>
 */
public enum BillingStatus {
    PENDING,
    COMPLETED,
    FAILED,
    SKIPPED
}
