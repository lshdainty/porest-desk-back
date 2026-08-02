package com.porest.desk.dataimport.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 업로드 파일(CSV / Excel)을 {@link ParsedFile}(헤더 + 행)로 파싱. static util.
 *
 * <p>CSV 는 RFC4180(따옴표·이스케이프·필드 내 개행) 문자 단위 파서 + UTF-8 BOM 제거.
 * Excel(xlsx/xls)은 POI {@link WorkbookFactory} 자동 판별, 날짜 서식 셀은 ISO 문자열로,
 * 숫자 셀은 불필요한 소수 0 을 제거한 평문으로 정규화한다(값 해석은 {@link ValueNormalizer}).
 */
public final class FileParser {

    private FileParser() {}

    /**
     * 방어적 행 상한 — 초대용량 파일로 인한 OOM 방지.
     *
     * <p>업로드 자체가 {@code spring.servlet.multipart.max-file-size}(기본 10MB)로 이미 제한되지만,
     * 엑셀은 압축돼 있어 같은 용량에 훨씬 많은 행이 들어간다. 그래서 행 수로도 한 겹 막는다.
     *
     * <p>넘으면 <b>조용히 자르지 않고 거부한다.</b> 예전엔 상한까지만 읽고 멈춰서,
     * 사용자는 전부 들어간 줄 알지만 나머지가 통째로 없어졌다.
     */
    private static final int MAX_ROWS = 200_000;

    public static ParsedFile parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidValueException(DeskErrorCode.IMPORT_EMPTY_FILE);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            ParsedFile parsed = (name.endsWith(".xlsx") || name.endsWith(".xls"))
                ? parseExcel(file)
                : parseCsv(file);
            if (parsed.headers().isEmpty()) {
                throw new InvalidValueException(DeskErrorCode.IMPORT_EMPTY_FILE);
            }
            return parsed;
        } catch (InvalidValueException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidValueException(DeskErrorCode.IMPORT_PARSE_FAILED);
        }
    }

    // ── CSV ──────────────────────────────────────────────────

    private static ParsedFile parseCsv(MultipartFile file) throws Exception {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1); // BOM
        }
        List<List<String>> all = parseCsvContent(content);
        if (all.isEmpty()) {
            return new ParsedFile(List.of(), List.of());
        }
        List<String> headers = trimTrailingEmpty(all.get(0));
        int width = headers.size();
        List<List<String>> rows = new ArrayList<>();
        for (int r = 1; r < all.size(); r++) {
            List<String> normalized = normalizeWidth(all.get(r), width);
            if (isBlankRow(normalized)) continue;
            rows.add(normalized);
            ensureWithinLimit(rows.size());
        }
        return new ParsedFile(headers, rows);
    }

    /** RFC4180 문자 단위 파서 — 따옴표 내 콤마/개행 보존, "" → ". */
    static List<List<String>> parseCsvContent(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0, n = content.length();
        while (i < n) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && content.charAt(i + 1) == '"') { field.append('"'); i += 2; }
                    else { inQuotes = false; i++; }
                } else { field.append(c); i++; }
            } else {
                if (c == '"') { inQuotes = true; i++; }
                else if (c == ',') { cur.add(field.toString()); field.setLength(0); i++; }
                else if (c == '\r') { i++; }
                else if (c == '\n') { cur.add(field.toString()); field.setLength(0); rows.add(cur); cur = new ArrayList<>(); i++; }
                else { field.append(c); i++; }
            }
        }
        if (field.length() > 0 || !cur.isEmpty()) { cur.add(field.toString()); rows.add(cur); }
        return rows;
    }

    // ── Excel ────────────────────────────────────────────────

    private static ParsedFile parseExcel(MultipartFile file) throws Exception {
        try (var is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return new ParsedFile(List.of(), List.of());
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return new ParsedFile(List.of(), List.of());

            int width = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < width; c++) headers.add(cellToString(headerRow.getCell(c)));
            headers = trimTrailingEmpty(headers);
            int w = headers.size();

            List<List<String>> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            for (int r = sheet.getFirstRowNum() + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < w; c++) cells.add(cellToString(row.getCell(c)));
                if (isBlankRow(cells)) continue;
                rows.add(cells);
                ensureWithinLimit(rows.size());
            }
            return new ParsedFile(headers, rows);
        }
    }

    static String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> numericToString(cell);
            case FORMULA -> {
                try { yield numericToString(cell); }
                catch (Exception e) {
                    try { yield cell.getStringCellValue().trim(); } catch (Exception e2) { yield ""; }
                }
            }
            default -> "";
        };
    }

    private static String numericToString(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        double d = cell.getNumericCellValue();
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    // ── 공통 ─────────────────────────────────────────────────

    /** 상한 초과는 조용히 자르지 않고 거부한다 — 잘린 줄 모르고 넘어가는 게 더 위험하다. */
    private static void ensureWithinLimit(int rowCount) {
        if (rowCount > MAX_ROWS) {
            throw new InvalidValueException(DeskErrorCode.IMPORT_TOO_MANY_ROWS);
        }
    }

    private static List<String> normalizeWidth(List<String> row, int width) {
        List<String> out = new ArrayList<>(width);
        for (int i = 0; i < width; i++) out.add(i < row.size() ? row.get(i) : "");
        return out;
    }

    private static boolean isBlankRow(List<String> row) {
        for (String c : row) if (c != null && !c.isBlank()) return false;
        return true;
    }

    /** 헤더 끝의 빈 열 제거(엑셀 trailing 빈 헤더 방어). 중간 빈 헤더는 유지. */
    private static List<String> trimTrailingEmpty(List<String> headers) {
        int end = headers.size();
        while (end > 0 && (headers.get(end - 1) == null || headers.get(end - 1).isBlank())) end--;
        return new ArrayList<>(headers.subList(0, end));
    }
}
