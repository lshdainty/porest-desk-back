package com.porest.desk.dataimport.type;

/**
 * 가져오기 열 매핑의 대상(우리 가계부) 필드.
 *
 * <p>원본 파일의 각 열을 이 필드 중 하나에 연결한다. DATE/TYPE/AMOUNT 는 거래 생성에 필수.
 * 은행 거래내역(토스 등)처럼 출금/입금이 분리된 경우 AMOUNT_OUT/AMOUNT_IN 을 매핑하면
 * 부호·유형을 파생한다(이때 AMOUNT/TYPE 매핑은 불필요).
 */
public enum ImportField {
    DATE,         // 거래 일시
    TYPE,         // 수입/지출
    AMOUNT,       // 금액(단일 컬럼)
    AMOUNT_OUT,   // 출금액(지출) — 은행 거래내역 분리형
    AMOUNT_IN,    // 입금액(수입) — 은행 거래내역 분리형
    CATEGORY,     // 카테고리(대분류)
    SUBCATEGORY,  // 소분류(설명에 병합)
    ASSET,        // 자산·결제수단
    MEMO,         // 설명·메모·거래처
}
