package com.porest.desk.asset.type;

/**
 * 투자 자산의 거래 유형.
 *
 * <p>{@link #OPENING} 은 돈이 오가지 않는다 — 앱을 쓰기 전부터 갖고 있던 보유를 적어 두는 것이라
 * 예수금을 건드리지 않고 수량·원가만 세운다. {@link #BUY}/{@link #SELL} 만 예수금이 움직인다.
 */
public enum TradeType {
    OPENING,
    BUY,
    SELL;

    /** 예수금이 움직이는 거래인가 — 기초 보유는 기록일 뿐 돈이 오가지 않는다. */
    public boolean movesCash() {
        return this != OPENING;
    }
}
