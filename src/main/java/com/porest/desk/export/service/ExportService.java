package com.porest.desk.export.service;

import com.porest.desk.export.controller.dto.ExportApiDto;

import java.io.IOException;
import java.io.OutputStream;

public interface ExportService {

    /** 파일명/Content-Type 등 응답 헤더 정보 (데이터 조회 없이 계산). */
    record ExportDescriptor(String filename, String contentType) {}

    ExportDescriptor describe(ExportApiDto.ExportRequest req);

    /** 선택 조건으로 파일을 생성해 out 으로 스트리밍 기록. */
    void writeExport(OutputStream out, ExportApiDto.ExportRequest req, Long userRowId) throws IOException;

    /** 선택 종 + 기간의 건수. */
    ExportApiDto.CountResponse counts(ExportApiDto.CountRequest req, Long userRowId);

    /** 선택 종 + 기간의 미리보기(종별 상위 N행). */
    ExportApiDto.PreviewResponse preview(ExportApiDto.PreviewRequest req, Long userRowId);
}
