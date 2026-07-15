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
    boolean duplicate,
    String error
) {
    public boolean valid() {
        return error == null;
    }

    public StandardRow withDuplicate(boolean dup) {
        return new StandardRow(lineNo, date, type, amount, category, subcategory, asset, memo, dup, error);
    }
}
