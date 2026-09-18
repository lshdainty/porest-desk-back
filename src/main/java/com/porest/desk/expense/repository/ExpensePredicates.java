package com.porest.desk.expense.repository;

import static com.porest.desk.expense.domain.QExpense.expense;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.Expense;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 질의에서 "집계에 넣을 거래" 를 고르는 조건 — <b>한 곳에 모아 둔다.</b>
 *
 * <p>같은 규칙을 질의마다 손으로 다시 쓰다가 실제로 빠뜨렸다: 예산 이행률 차트만 옛 규칙으로
 * 남아 상단 카드(20361%)와 차트(20460%)가 달랐다. 조건을 이름으로 부르면 새 질의도
 * 빠뜨리기 어렵다.
 *
 * <p>in-memory 쪽 짝은 {@code ExpenseAggregates} 다 — 목록을 이미 들고 있는 자리는 그쪽을
 * 쓴다. 규칙이 갈리지 않게 <b>둘의 뜻은 같아야 한다.</b>
 */
public final class ExpensePredicates {

    private ExpensePredicates() {}

    /**
     * 돈이 살아 있는 거래 — 지우지 않았고 환불되지도 않은 것.
     *
     * <p>카드 쪽(청구·할부 회차·실적·잔액 흐름)이 쓰는 기준이다.
     */
    public static BooleanExpression countable() {
        return expense.isDeleted.eq(YNType.N).and(expense.refundedAt.isNull());
    }

    /**
     * <b>가계부</b> 집계에 넣을 거래 — {@link #countable()} 에서 카드 이월을 뺀다.
     *
     * <p>카드를 만들 때 적은 "이전 미결제 사용액"({@code CARD_CARRYOVER}, D4)은 앱을 쓰기
     * 전에 이미 쓴 돈이라 <b>등록한 달의 지출이 아니다.</b> 그걸 지출 합계에 세면 카드를
     * 등록한 달만 몇십만 원이 솟는다.
     *
     * <p>반대로 카드 쪽에서는 빼면 안 된다 — 그 거래가 곧 카드의 미결제 잔액이고 첫 회차
     * 청구다. 빼는 순간 D4(잔액 = 거래 합)가 풀린다.
     */
    public static BooleanExpression ledgerCountable() {
        return countable().and(
            expense.autoSource.isNull()
                .or(expense.autoSource.ne(Expense.AUTO_SOURCE_CARD_CARRYOVER)));
    }
}
