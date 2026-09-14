package com.porest.desk.expense.type;

/**
 * 반복 거래·프리셋이 만들어 낼 거래의 종류.
 *
 * <p>{@link ExpenseType} 에 TRANSFER 를 넣지 않은 이유는, 그 enum 이 <b>지출/수입 한 건</b>을
 * 뜻하는 자리에서 쓰이기 때문이다 — 카테고리의 종류, 통계 분류, 잔액 이력의 방향.
 * 이체는 그중 어느 것도 아니다(자산 간 이동이라 순자산 증감이 0 이고 카테고리가 없다).
 * TRANSFER 를 거기 끼우면 그 모든 자리가 "이체면 어떻게 하지" 를 떠안는다.
 *
 * <p>그래서 <b>예약을 표현하는 두 테이블에서만</b> 쓰는 종류를 따로 둔다. 컬럼은 그대로
 * {@code expense_type}(varchar) 이고 값만 하나 는다.
 */
public enum TxKind {
    EXPENSE,
    INCOME,
    TRANSFER;

    public boolean isTransfer() {
        return this == TRANSFER;
    }

    /**
     * 지출/수입으로 좁힌다 — 실제 {@code Expense} 를 만들 때 쓴다.
     *
     * @throws IllegalStateException TRANSFER 에서 부르면. 호출부가 분기를 빠뜨렸다는 뜻이다.
     */
    public ExpenseType toExpenseType() {
        return switch (this) {
            case EXPENSE -> ExpenseType.EXPENSE;
            case INCOME -> ExpenseType.INCOME;
            case TRANSFER -> throw new IllegalStateException(
                "TRANSFER 는 Expense 가 아니다 — 호출부가 이체 분기를 빠뜨렸다");
        };
    }

    public static TxKind from(ExpenseType type) {
        return type == ExpenseType.INCOME ? INCOME : EXPENSE;
    }
}
