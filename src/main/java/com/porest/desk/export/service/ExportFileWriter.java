package com.porest.desk.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 내보내기 표를 형식별로 OutputStream 에 기록. 어느 메서드도 OutputStream 을 닫지 않음
 * (ZIP 엔트리/스트리밍 응답에서 상위가 스트림 수명을 관리).
 */
public final class ExportFileWriter {

    private ExportFileWriter() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 단일 표 → CSV (UTF-8 + BOM, RFC4180 따옴표). */
    public static void writeCsv(OutputStream out, ExportTable table) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('﻿'); // BOM — Excel 한글 깨짐 방지
        writeCsvRow(w, table.headers());
        for (List<String> row : table.rows()) {
            writeCsvRow(w, row);
        }
        w.flush();
    }

    private static void writeCsvRow(Writer w, List<String> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i);
            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    /** 단일 표 → JSON (헤더를 키로 한 객체 배열, UTF-8). */
    public static void writeJson(OutputStream out, ExportTable table) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        List<String> headers = table.headers();
        for (List<String> row : table.rows()) {
            Map<String, String> obj = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                obj.put(headers.get(i), i < row.size() ? row.get(i) : "");
            }
            records.add(obj);
        }
        out.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(records));
        out.flush();
    }

    /** 다수 표 → 한 워크북의 종류별 시트 (SXSSF 스트리밍, 저메모리). */
    public static void writeExcel(OutputStream out, List<ExportTable> tables) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            for (ExportTable table : tables) {
                Sheet sheet = wb.createSheet(sheetName(table.type().displayName()));
                Row header = sheet.createRow(0);
                List<String> headers = table.headers();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = header.createCell(c);
                    cell.setCellValue(headers.get(c));
                }
                int r = 1;
                for (List<String> row : table.rows()) {
                    Row xr = sheet.createRow(r++);
                    for (int c = 0; c < row.size(); c++) {
                        xr.createCell(c).setCellValue(row.get(c) == null ? "" : row.get(c));
                    }
                }
            }
            wb.write(out);
            out.flush();
            wb.dispose();
        }
    }

    /** Excel 시트명 제약(31자, : \ / ? * [ ] 금지) 정리. */
    private static String sheetName(String name) {
        String cleaned = name.replaceAll("[:\\\\/?*\\[\\]]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
