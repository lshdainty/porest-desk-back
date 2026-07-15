package com.porest.desk.dataimport.service;

import java.util.List;

/**
 * 파싱된 원본 파일 — 헤더 1행 + 데이터 행들(셀 문자열 리스트).
 * CSV/Excel 파서가 공통으로 산출. 빈 셀은 "" 로 채워 열 정렬을 보존한다.
 */
public record ParsedFile(
    List<String> headers,
    List<List<String>> rows
) {}
