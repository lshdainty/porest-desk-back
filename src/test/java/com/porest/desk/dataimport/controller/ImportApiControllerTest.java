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
            ExpenseType.EXPENSE, 5700L, "식비", null, "체크카드", "편의점", "당근마켓", null, false, null);
        given(importService.analyze(any(), eq(ImportSource.EASYBUDGET), eq(1L)))
            .willReturn(new ImportService.AnalyzeResult(
                "t.csv", 2, 2, 0,
                List.of("기간", "금액"),
                Map.of(ImportField.DATE, 0, ImportField.AMOUNT, 1),
                List.of(row), List.of(), List.of("싟비", "여행 > 기타"), 2));

        MockMultipartFile file = new MockMultipartFile("file", "t.csv", "text/csv",
            "기간,금액\n2026-05-28,5700\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/import/analyze").file(file).param("source", "EASYBUDGET"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalRows").value(2))
            .andExpect(jsonPath("$.data.validRows").value(2))
            .andExpect(jsonPath("$.data.columns[0].name").value("기간"))
            .andExpect(jsonPath("$.data.suggestedMapping.DATE").value(0))
            .andExpect(jsonPath("$.data.preview[0].amount").value(5700))
            .andExpect(jsonPath("$.data.preview[0].type").value("EXPENSE"))
            .andExpect(jsonPath("$.data.preview[0].merchant").value("당근마켓"))
            // 실행 전에 "무엇이 새로 생기는지" 가 응답에 실려야 화면이 물어볼 수 있다.
            .andExpect(jsonPath("$.data.newCategories[0]").value("싟비"))
            .andExpect(jsonPath("$.data.newCategories[1]").value("여행 > 기타"))
            .andExpect(jsonPath("$.data.newCategoryCount").value(2));

        verify(importService).analyze(any(), eq(ImportSource.EASYBUDGET), eq(1L));
    }

    @Test
    @DisplayName("POST /import/execute — 파일 + 매핑·옵션(JSON part)으로 저장 위임")
    void execute() throws Exception {
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(2, 1, 0, List.of(), List.of("싟비"), 1, 0, List.of()));

        mockMvc.perform(multipart("/api/v1/import/execute").file(csvFile()).file(executeRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imported").value(2))
            .andExpect(jsonPath("$.data.skipped").value(1))
            .andExpect(jsonPath("$.data.failed").value(0))
            .andExpect(jsonPath("$.data.failuresTruncated").value(false))
            .andExpect(jsonPath("$.data.createdCategories[0]").value("싟비"))
            .andExpect(jsonPath("$.data.createdCategoryCount").value(1))
            .andExpect(jsonPath("$.data.duplicateSkipped").value(0))
            .andExpect(jsonPath("$.data.duplicatesTruncated").value(false));

        verify(importService).execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L));
    }

    @Test
    @DisplayName("POST /import/execute — 실패 행의 줄번호·사유를 그대로 내려준다")
    void execute_returnsFailureRows() throws Exception {
        // 화면이 "실패 2" 숫자만 띄우면 사용자가 CSV 를 못 고친다. 어느 줄이 왜 실패했는지 실어 보낸다.
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(0, 20, 2,
                List.of(new ImportService.Failure(21, "amount"), new ImportService.Failure(22, "date")),
                List.of(), 0, 0, List.of()));

        mockMvc.perform(multipart("/api/v1/import/execute").file(csvFile()).file(executeRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.failures[0].lineNo").value(21))
            .andExpect(jsonPath("$.data.failures[0].reason").value("amount"))
            .andExpect(jsonPath("$.data.failures[1].lineNo").value(22))
            .andExpect(jsonPath("$.data.failures[1].reason").value("date"))
            .andExpect(jsonPath("$.data.failuresTruncated").value(false));
    }

    @Test
    @DisplayName("POST /import/execute — 실패 목록이 상한에서 잘리면 잘렸다고 알린다")
    void execute_flagsTruncatedFailures() throws Exception {
        // 실패 120 인데 목록이 50줄이면 화면은 나머지 70줄을 조용히 잃는다 — 잘렸다는 사실을 응답에 남긴다.
        List<ImportService.Failure> capped = java.util.stream.IntStream.rangeClosed(1, 50)
            .mapToObj(i -> new ImportService.Failure(i, "amount"))
            .toList();
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(0, 0, 120, capped, List.of(), 0, 0, List.of()));

        mockMvc.perform(multipart("/api/v1/import/execute").file(csvFile()).file(executeRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.failed").value(120))
            .andExpect(jsonPath("$.data.failures.length()").value(50))
            .andExpect(jsonPath("$.data.failuresTruncated").value(true));
    }

    @Test
    @DisplayName("POST /import/analyze — 새 카테고리 목록이 잘려도 전체 개수는 그대로 내려준다")
    void analyze_keepsNewCategoryCountWhenListIsCapped() throws Exception {
        // 카테고리 열을 잘못 매핑하면 행 수만큼 새 이름이 나온다. 목록은 잘라도
        // "3,000개가 만들어져요" 라는 경고는 살아 있어야 한다.
        List<String> capped = java.util.stream.IntStream.rangeClosed(1, 50)
            .mapToObj(i -> "새카테고리" + i)
            .toList();
        given(importService.analyze(any(), eq(ImportSource.POREST), eq(1L)))
            .willReturn(new ImportService.AnalyzeResult(
                "t.csv", 3000, 3000, 0,
                List.of("날짜", "금액"),
                Map.of(ImportField.DATE, 0, ImportField.AMOUNT, 1),
                List.of(), List.of(), capped, 3000));

        mockMvc.perform(multipart("/api/v1/import/analyze").file(csvFile()).param("source", "POREST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.newCategories.length()").value(50))
            .andExpect(jsonPath("$.data.newCategoryCount").value(3000));
    }

    @Test
    @DisplayName("POST /import/execute — 중복으로 건너뛴 행을 개수와 함께 내려준다")
    void execute_returnsSkippedDuplicates() throws Exception {
        // "가져오기 성공" 이라는데 방금 올린 행이 목록에 없다 — 화면이 그 이유를 말하려면 이 두 값이 필요하다.
        StandardRow dup = new StandardRow(7, java.time.LocalDateTime.of(2026, 5, 28, 10, 0),
            ExpenseType.EXPENSE, 500L, "식비", null, "체크카드", null, "동네카페", null, true, null);
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(0, 1, 0, List.of(), List.of(), 0, 1, List.of(dup)));

        mockMvc.perform(multipart("/api/v1/import/execute").file(csvFile()).file(executeRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicateSkipped").value(1))
            .andExpect(jsonPath("$.data.duplicates[0].lineNo").value(7))
            .andExpect(jsonPath("$.data.duplicates[0].amount").value(500))
            .andExpect(jsonPath("$.data.duplicates[0].merchant").value("동네카페"))
            .andExpect(jsonPath("$.data.duplicates[0].duplicate").value(true))
            .andExpect(jsonPath("$.data.duplicatesTruncated").value(false))
            // 기존 필드는 그대로 — 옛 앱이 읽는다.
            .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    @DisplayName("POST /import/execute — 중복 목록이 상한에서 잘리면 잘렸다고 알린다")
    void execute_flagsTruncatedDuplicates() throws Exception {
        List<StandardRow> capped = java.util.stream.IntStream.rangeClosed(1, 50)
            .mapToObj(i -> new StandardRow(i, java.time.LocalDateTime.of(2026, 5, 28, 0, 0),
                ExpenseType.EXPENSE, 500L, "식비", null, null, null, null, null, true, null))
            .toList();
        given(importService.execute(any(), eq(ImportSource.POREST), any(), eq(true), eq(true), eq(1L)))
            .willReturn(new ImportService.ExecuteResult(0, 300, 0, List.of(), List.of(), 0, 300, capped));

        mockMvc.perform(multipart("/api/v1/import/execute").file(csvFile()).file(executeRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicateSkipped").value(300))
            .andExpect(jsonPath("$.data.duplicates.length()").value(50))
            .andExpect(jsonPath("$.data.duplicatesTruncated").value(true));
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "t.csv", "text/csv",
            "날짜,금액\n2026-05-28,5700\n".getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile executeRequest() {
        return new MockMultipartFile("request", "", "application/json",
            "{\"source\":\"POREST\",\"mapping\":{\"DATE\":0,\"AMOUNT\":1},\"dupSkip\":true,\"autoCat\":true}"
                .getBytes(StandardCharsets.UTF_8));
    }
}
