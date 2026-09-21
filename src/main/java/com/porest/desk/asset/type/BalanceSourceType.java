package com.porest.desk.asset.type;

/**
 * 자산 잔액 변동 이력(asset_balance_history)의 출처 타입.
 *
 * <p>absolute = 그 시점의 "절대 잔액"을 못 박는 앵커. flow = 직전 잔액에 더해지는 "부호 있는 변동액".
 * 잔액 조회는 "기준시각 이하의 가장 최신 absolute 앵커 + 그 이후 flow 합" 으로 계산한다.
 *
 * <ul>
 *   <li>{@link #INIT} (absolute) — 자산 등록 시점 초기 잔액</li>
 *   <li>{@link #MANUAL} (absolute) — 사용자가 잔액을 직접 수정(점프). 가계부 통계엔 영향 없음</li>
 *   <li>{@link #VALUATION} (absolute) — 투자 평가액 갱신(현재는 수동, 추후 증권사 API 연동 지점)</li>
 *   <li>{@link #EXPENSE} (flow) — 수입/지출 거래. INCOME=+amount, EXPENSE=-amount</li>
 *   <li>{@link #TRANSFER} (flow) — 자산 이체. 출금자산=-(amount+fee), 입금자산=+amount</li>
 *   <li>{@link #TRADE} (flow) — 매수·매도. 매수=-(대금+수수료), 매도=+(대금-수수료). 예수금만 움직인다</li>
 *   <li>{@link #CARD_SETTLED} (flow) — 신용카드 빚을 <b>이체 없이</b> 정리한 몫. source = 거래.
 *       닫힌 회차에 소급 입력한 지출(기록용)은 현실에서 이미 결제가 끝났으므로 +금액으로 빚을
 *       상계하고, 기한 지난 결제분을 지우면 −금액으로 잔액을 붙잡아 과납 스윕이 돌려주지 않게 한다</li>
 *   <li>{@link #CARD_REFUND_HOLD} (flow) — 환불 마크 때 돌려주지 않고 붙잡은 몫. source = 거래.
 *       {@link #CARD_SETTLED} 와 따로 두는 까닭은 환불 취소가 이것만 지워 환불 전 모습으로 정확히
 *       돌아가게 하려는 것이다</li>
 * </ul>
 */
public enum BalanceSourceType {
    INIT(true),
    MANUAL(true),
    VALUATION(true),
    EXPENSE(false),
    TRANSFER(false),
    TRADE(false),
    CARD_SETTLED(false),
    CARD_REFUND_HOLD(false);

    private final boolean absolute;

    BalanceSourceType(boolean absolute) {
        this.absolute = absolute;
    }

    /** true 면 절대 잔액 앵커, false 면 부호 있는 변동(flow). */
    public boolean isAbsolute() {
        return absolute;
    }
}
