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
import java.util.regex.Pattern;

/**
 * 내보내기 표를 형식별로 OutputStream 에 기록. 어느 메서드도 OutputStream 을 닫지 않음
 * (ZIP 엔트리/스트리밍 응답에서 상위가 스트림 수명을 관리).
 *
 * <p>CSV 만 수식 주입 가드({@link #guardFormula})를 지난다. JSON·Excel 은 대상이 아니다 —
 * JSON 은 문자열 값이고, POI {@code setCellValue(String)} 는 문자열 셀을 만들어 수식으로 평가되지 않는다.
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
            // 가드가 먼저다 — 인용 뒤에 붙이면 접두가 따옴표 바깥으로 나가 셀이 깨진다.
            v = guardFormula(v);
            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    /** 스프레드시트가 수식으로 읽는 선두 문자 (OWASP CSV Injection: = + - @ TAB CR). */
    private static final String FORMULA_LEADS = "=+-@\t\r";

    /**
     * 순수 십진수는 수식이 될 수 없다 — 음수 잔액({@code -5000000})까지 텍스트로 만들지 않기 위한 예외.
     * 부채 계열 자산 잔액은 항상 음수라 이 예외가 없으면 금액 열 전체가 문자열이 되어 SUM 이 깨진다.
     */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^[+-]?\\d+(\\.\\d+)?$");

    /**
     * 수식 주입 차단 — 선두가 수식 문자면 공백 한 칸을 붙여 텍스트로 만든다.
     *
     * <p>인용({@code "..."})은 방어가 아니다. 엑셀·구글시트는 따옴표를 벗긴 뒤 {@code =1+1} 을
     * 수식으로 평가한다(외부 링크·DDE). 접두는 {@code '} 가 아니라 <b>공백</b>을 쓴다 —
     * 가져오기(ImportColumnMapper.get)가 셀을 {@code trim()} 하므로 내보낸 CSV 재업로드에서
     * 원래 값이 그대로 복원되고, 중복 판정 키(날짜|금액|설명)도 어긋나지 않는다.
     */
    private static String guardFormula(String v) {
        if (v.isEmpty()) return v;
        if (FORMULA_LEADS.indexOf(v.charAt(0)) < 0) return v;
        if (PLAIN_NUMBER.matcher(v).matches()) return v;
        return " " + v;
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
