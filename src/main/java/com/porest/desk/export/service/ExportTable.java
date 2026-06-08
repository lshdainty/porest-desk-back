package com.porest.desk.export.service;

import com.porest.desk.export.type.ExportType;

import java.util.List;

/**
 * 한 데이터 종류의 내보내기 표 — 헤더 + 행(셀 문자열 리스트).
 * CSV/Excel/JSON writer 가 공통으로 소비.
 */
public record ExportTable(
    ExportType type,
    List<String> headers,
    List<List<String>> rows
) {}
