package com.porest.desk.dataimport.service;

import com.porest.core.exception.InvalidValueException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileParser — CSV / Excel 파싱")
class FileParserTest {

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "t.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("CSV — 헤더 + 행, 따옴표 내 콤마 보존")
    void csv_basic() {
        ParsedFile p = FileParser.parse(csv("날짜,금액,메모\n2026-05-28,5700,편의점\n2026-05-27,\"1,000\",\"메모,콤마\"\n"));
        assertThat(p.headers()).containsExactly("날짜", "금액", "메모");
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(1)).containsExactly("2026-05-27", "1,000", "메모,콤마");
    }

    @Test
    @DisplayName("CSV — UTF-8 BOM 제거")
    void csv_bom_stripped() {
        ParsedFile p = FileParser.parse(csv("﻿날짜,금액\n2026-01-01,100\n"));
        assertThat(p.headers()).containsExactly("날짜", "금액");
    }

    @Test
    @DisplayName("CSV — 빈 행 스킵, 짧은 행 폭 정규화")
    void csv_blank_and_short_rows() {
        ParsedFile p = FileParser.parse(csv("A,B,C\n1,2,3\n\n4,5\n"));
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(1)).containsExactly("4", "5", ""); // 폭 3 으로 pad
    }

    @Test
    @DisplayName("빈 파일은 예외")
    void empty_file_throws() {
        MockMultipartFile empty = new MockMultipartFile("file", "t.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> FileParser.parse(empty)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("Excel(xlsx) — 헤더 + 숫자 셀은 평문화, 빈 셀 유지")
    void excel_roundtrip() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("내역");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("기간");
            h.createCell(1).setCellValue("분류");
            h.createCell(2).setCellValue("금액");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026-05-28");
            r1.createCell(1).setCellValue("식비");
            r1.createCell(2).setCellValue(5700); // 숫자 셀
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("2026-05-27");
            // 분류(1) 비움 → 빈 셀
            r2.createCell(2).setCellValue(13460);
            wb.write(bos);
            bytes = bos.toByteArray();
        }
        MockMultipartFile xlsx = new MockMultipartFile("file", "t.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ParsedFile p = FileParser.parse(xlsx);

        assertThat(p.headers()).containsExactly("기간", "분류", "금액");
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0)).containsExactly("2026-05-28", "식비", "5700");
        assertThat(p.rows().get(1)).containsExactly("2026-05-27", "", "13460");
    }
}
