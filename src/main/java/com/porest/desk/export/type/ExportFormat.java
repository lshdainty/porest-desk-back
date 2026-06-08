package com.porest.desk.export.type;

/**
 * 내보내기 파일 형식. PDF 는 스코프 제외(추후 별도).
 */
public enum ExportFormat {
    CSV("csv", "text/csv; charset=UTF-8"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    JSON("json", "application/json; charset=UTF-8");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
