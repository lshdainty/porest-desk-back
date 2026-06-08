package com.porest.desk.export.type;

/**
 * 내보내기 대상 데이터 종류 (디자인 7종). 더치페이 제외.
 *
 * <p>periodScoped=true 인 타입만 선택 기간으로 필터링된다.
 * 자산·카테고리·메모는 트랜잭션성 날짜축이 없어 기간 무관 전체 스냅샷.
 */
public enum ExportType {
    EXPENSE("expense", "거래 내역", true),
    ASSET("asset", "자산·계좌", false),
    BUDGET("budget", "예산 설정", true),
    CATEGORY("category", "카테고리", false),
    MEMO("memo", "메모", false),
    CALENDAR("calendar", "캘린더 일정", true),
    TODO("todo", "할 일", true);

    private final String slug;
    private final String displayName;
    private final boolean periodScoped;

    ExportType(String slug, String displayName, boolean periodScoped) {
        this.slug = slug;
        this.displayName = displayName;
        this.periodScoped = periodScoped;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isPeriodScoped() {
        return periodScoped;
    }
}
