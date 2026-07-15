package com.porest.desk.dataimport.type;

/**
 * 가져오기 원본(프리셋). 각 앱의 실제 export 형식에 맞춘 열 자동감지 규칙을
 * {@code ImportColumnMapper} 가 소스별로 제공한다. 사용자는 매핑 UI 에서 최종 보정한다.
 */
public enum ImportSource {
    /** Porest 자체 내보내기 파일(round-trip). 헤더: 날짜/유형/카테고리/자산/금액/설명/거래처/결제수단. */
    POREST("porest", "Porest"),
    /** 편한가계부 = 머니매니저(Realbyte, com.realbyteapps.moneymanagerfree). 위치 고정 A~G. */
    EASYBUDGET("easybudget", "편한가계부·머니매니저"),
    /** 뱅크샐러드 '가계부 내역'. 헤더: 날짜/시간/타입/대분류/소분류/내용/금액/화폐/결제수단/메모. */
    BANKSALAD("banksalad", "뱅크샐러드"),
    /** 토스(토스뱅크 거래내역). 출금액/입금액 분리 → 유형 파생. */
    TOSS("toss", "토스"),
    /** 직접 만든 파일 — 자동감지 best-effort, 사용자가 전 열 수동 매핑. */
    CUSTOM("custom", "직접 매핑");

    private final String slug;
    private final String displayName;

    ImportSource(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }
}
