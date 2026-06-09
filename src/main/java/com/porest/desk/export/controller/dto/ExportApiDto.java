package com.porest.desk.export.controller.dto;

import com.porest.desk.export.type.ExportFormat;
import com.porest.desk.export.type.ExportPeriod;
import com.porest.desk.export.type.ExportType;

import java.time.LocalDate;
import java.util.List;

/**
 * 데이터 내보내기 API 요청/응답 DTO.
 */
public class ExportApiDto {

    /** 파일 생성·다운로드 요청. */
    public record ExportRequest(
        ExportFormat format,
        ExportPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        List<ExportType> types,
        boolean mask
    ) {}

    /** 건수 조회 요청 (선택 종 + 기간). */
    public record CountRequest(
        ExportPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        List<ExportType> types
    ) {}

    /** 미리보기 요청 (선택 종 + 기간, 종별 상위 N행). */
    public record PreviewRequest(
        ExportPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        List<ExportType> types
    ) {}

    public record TypeCount(
        String type,        // ExportType.slug()
        String displayName,
        long count
    ) {}

    public record CountResponse(
        List<TypeCount> counts
    ) {}

    public record PreviewTable(
        String type,        // ExportType.slug()
        String displayName,
        List<String> headers,
        List<List<String>> rows,
        long totalCount
    ) {}

    public record PreviewResponse(
        List<PreviewTable> tables
    ) {}
}
