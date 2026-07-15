package com.porest.desk.dataimport.controller.dto;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;

import java.util.List;
import java.util.Map;

/**
 * 데이터 가져오기 API 요청/응답 DTO.
 */
public class ImportApiDto {

    /** 원본 파일의 열(매핑 UI 의 select 옵션). */
    public record ColumnInfo(int index, String name) {}

    /** 미리보기 한 행(매핑·정규화 적용 결과). */
    public record PreviewRow(
        int lineNo,
        String date,       // ISO, null 이면 파싱 실패
        String type,       // INCOME/EXPENSE, null 이면 미상
        Long amount,
        String category,
        String asset,
        String memo,
        boolean duplicate,
        String error       // date/amount/type 등 오류 코드, null 이면 정상
    ) {}

    /** analyze 응답. */
    public record AnalyzeResponse(
        String fileName,
        int totalRows,
        int validRows,
        int duplicateCount,
        List<ColumnInfo> columns,
        Map<ImportField, Integer> suggestedMapping,
        List<PreviewRow> preview
    ) {}

    /** execute 요청(JSON part). mapping: 필드→열인덱스. */
    public record ExecuteRequest(
        ImportSource source,
        Map<ImportField, Integer> mapping,
        boolean dupSkip,
        boolean autoCat
    ) {}

    /** execute 응답. */
    public record ExecuteResponse(
        int imported,
        int skipped,
        int failed,
        List<FailureRow> failures
    ) {}

    public record FailureRow(int lineNo, String reason) {}
}
