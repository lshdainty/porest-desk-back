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
 * </ul>
 */
public enum BalanceSourceType {
    INIT(true),
    MANUAL(true),
    VALUATION(true),
    EXPENSE(false),
    TRANSFER(false),
    TRADE(false);

    private final boolean absolute;

    BalanceSourceType(boolean absolute) {
        this.absolute = absolute;
    }

    /** true 면 절대 잔액 앵커, false 면 부호 있는 변동(flow). */
    public boolean isAbsolute() {
        return absolute;
    }
}
