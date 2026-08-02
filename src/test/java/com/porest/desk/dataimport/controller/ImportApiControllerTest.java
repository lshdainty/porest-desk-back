package com.porest.desk.dataimport.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.dataimport.service.ImportService;
import com.porest.desk.dataimport.service.StandardRow;
import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import API 슬라이스 테스트. 보안 필터는 끄고 {@link WithLoginUser} 로 로그인 사용자 주입.
 * analyze/execute 모두 multipart 업로드.
 */
@WebMvcTest(controllers = ImportApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ImportApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ImportService importService;
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST /import/analyze — 파일+소스로 자동매핑·미리보기 위임")
    void analyze() throws Exception {
        StandardRow row = new StandardRow(1, java.time.LocalDateTime.of(2026, 5, 28, 0, 0),
            ExpenseType.EXPENSE, 5700L, "식비", null, "체크카드", "편의점", null, null, false, null);
        given(importService.analyze(any(), eq(ImportSource.EASYBUDGET), eq(1L)))
            .willReturn(new ImportService.AnalyzeResult(
                "t.csv", 2, 2, 0,
                List.of("기간", "금액"),
                Map.of(ImportField.DATE, 0, ImportField.AMOUNT, 1),
                List.of(row), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "t.csv", "text/csv",
            "기간,금액\n2026-05-28,5700\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/import/analyze").file(file).param("source", "EASYBUDGET"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalRows").value(2))
            .andExpect(jsonPath("$.data.validRows").value(2))
            .andExpect(jsonPath("$.data.columns[0].name").value("기간"))
            .andExpect(jsonPath("$.data.suggestedMapping.DATE").value(0))
            .andExpect(jsonPath("$.data.preview[0].amount").value(5700))
            .andExpect(jsonPath("$.data.preview[0].type").value("EXPENSE"));

        verify(importService).analyze(any(), eq(ImportSource.EASYBUDGET), eq(1L));
    }

    @Test
    @DisplayName("POST /import/execute — 파일 + 매핑·옵션(JSON part)으로 저장 위임")
    void execute() throws Exception {
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(2, 1, 0, List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "t.csv", "text/csv",
            "날짜,금액\n2026-05-28,5700\n".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile request = new MockMultipartFile("request", "", "application/json",
            "{\"source\":\"POREST\",\"mapping\":{\"DATE\":0,\"AMOUNT\":1},\"dupSkip\":true,\"autoCat\":true}"
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/import/execute").file(file).file(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imported").value(2))
            .andExpect(jsonPath("$.data.skipped").value(1))
            .andExpect(jsonPath("$.data.failed").value(0));

        verify(importService).execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L));
    }
}
