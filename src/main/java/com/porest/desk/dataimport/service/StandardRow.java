package com.porest.desk.dataimport.service;

import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;

/**
 * 매핑·정규화를 거친 한 거래 행. analyze(미리보기)·execute(저장) 공통 중간표현.
 *
 * @param error 유효성 오류 사유 코드(null 이면 정상). "date"/"amount"/"type" 등 — 필수 값 누락/파싱 실패.
 */
public record StandardRow(
    int lineNo,
    LocalDateTime date,
    ExpenseType type,
    Long amount,
    String category,
    String subcategory,
    String asset,
    String memo,
    String merchant,
    String paymentMethod,
    boolean duplicate,
    String error
) {
    /** 이체 행 — 가계부 거래가 아니라 넣지 않지만 오류는 아니다(건너뜀으로 집계). */
    public static final String ERROR_TRANSFER = "transfer";

    /**
     * 대분류로 쓰려는 카테고리에 <b>거래가 직접 달려 있어</b> 자식을 만들 수 없는 행.
     *
     * <p>거래는 말단에만 달 수 있다(부모에 직접 달리면 합계가 이중 집계된다).
     * 실행 중에 행마다 터지면 이유를 알 수 없으므로 분석 단계에서 미리 표시한다.
     */
    public static final String ERROR_PARENT_HAS_TX = "parentHasTx";

    /** 넣지 않되 실패로 세지 않는 행인지. */
    public boolean skippable() {
        return ERROR_TRANSFER.equals(error);
    }

    public boolean valid() {
        return error == null;
    }

    public StandardRow withError(String newError) {
        return new StandardRow(lineNo, date, type, amount, category, subcategory, asset, memo,
            merchant, paymentMethod, duplicate, newError);
    }

    public StandardRow withDuplicate(boolean dup) {
        return new StandardRow(lineNo, date, type, amount, category, subcategory, asset, memo,
            merchant, paymentMethod, dup, error);
    }
}
