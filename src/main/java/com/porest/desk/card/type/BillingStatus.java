package com.porest.desk.card.type;

/**
 * 신용카드 청구(자동결제) 상태.
 * <ul>
 *   <li>PENDING   - 결제 예정(아직 미처리)</li>
 *   <li>COMPLETED - 결제 완료(이체 생성됨)</li>
 *   <li>FAILED    - 결제 실패(잔액 부족/결제계좌 미지정 등)</li>
 *   <li>SKIPPED   - 결제할 금액 없음(청구액 0)</li>
 *   <li>REFUNDED  - 그 회차에서 결제계좌로 <b>돌려준</b> 환급(환급 이체 연결). 순 납부액 =
 *       COMPLETED − REFUNDED 다. 종전엔 환급을 회차에 묶지 않아, 환급이 나간 회차에 지출이
 *       다시 늘어도 "이미 낸 돈" 이 줄지 않아 영원히 청구되지 않았다(QA 23차 결함 2)</li>
 *   <li>RECORD_REFUNDED - 기록용(앱이 결제하지 않은) 몫을 돌려준 환급. 앱이 낸 돈과 무관하므로
 *       순 납부액·크레딧에는 안 들어간다 — 들어가면 같은 회차 결제분의 환급이 그만큼 줄어든다(케이스 12)</li>
 * </ul>
 */
public enum BillingStatus {
    PENDING,
    COMPLETED,
    FAILED,
    SKIPPED,
    REFUNDED,
    RECORD_REFUNDED
}
