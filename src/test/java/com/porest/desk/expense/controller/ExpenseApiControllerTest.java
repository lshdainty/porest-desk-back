package com.porest.desk.expense.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Expense API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 매핑·바디 역직렬화·쿼리파라미터 바인딩·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = ExpenseApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ExpenseApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExpenseService expenseService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private ExpenseServiceDto.ExpenseInfo sampleInfo() {
        return new ExpenseServiceDto.ExpenseInfo(
                10L, 1L, 5L, "식비", "utensils", "#fff",
                2L, "현금", ExpenseType.EXPENSE, 15000L, "점심",
                LocalDateTime.of(2026, 7, 3, 12, 0), "김밥천국", "CARD",
                null, null,
            null,
            null,
            null, null, null,
            null, // autoSource — 손으로 쓴 거래
                LocalDateTime.of(2026, 7, 3, 12, 0), LocalDateTime.of(2026, 7, 3, 12, 0),
                List.of());
    }

    @Test
    @DisplayName("POST /expense — 로그인 사용자·바디로 createExpense 위임(날짜 유연 파싱)")
    void createExpense() throws Exception {
        given(expenseService.createExpense(any())).willReturn(sampleInfo());

        String body = """
                {"categoryRowId":5,"assetRowId":2,"expenseType":"EXPENSE","amount":15000,
                 "description":"점심","expenseDate":"2026-07-03","merchant":"김밥천국","paymentMethod":"CARD"}
                """;

        mockMvc.perform(post("/api/v1/expense")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rowId").value(10))
                .andExpect(jsonPath("$.data.amount").value(15000));

        var captor = ArgumentCaptor.forClass(ExpenseServiceDto.CreateCommand.class);
        verify(expenseService).createExpense(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().categoryRowId()).isEqualTo(5L);
        assertThat(captor.getValue().amount()).isEqualTo(15000L);
        assertThat(captor.getValue().expenseType()).isEqualTo(ExpenseType.EXPENSE);
        // "yyyy-MM-dd" → 해당 일자 00:00:00 으로 파싱
        assertThat(captor.getValue().expenseDate()).isEqualTo(LocalDateTime.of(2026, 7, 3, 0, 0));
    }

    @Test
    @DisplayName("GET /expenses — 쿼리파라미터(카테고리·자산·타입·기간) 매핑해 조회 위임")
    void getExpenses() throws Exception {
        given(expenseService.getExpenses(any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/expenses")
                        .param("categoryId", "5")
                        .param("assetId", "2")
                        .param("expenseType", "EXPENSE")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenses[0].rowId").value(10));

        verify(expenseService).getExpenses(1L, 5L, 2L, ExpenseType.EXPENSE,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("GET /expenses — 선택 파라미터 미지정이면 null 로 위임")
    void getExpensesNoParams() throws Exception {
        given(expenseService.getExpenses(any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/expenses"))
                .andExpect(status().isOk());

        verify(expenseService).getExpenses(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("GET /expenses?expenseType=INVALID — 잘못된 enum 값이면 400")
    void getExpensesInvalidEnum() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("expenseType", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /expense/{id} — id·로그인 사용자·바디(분할 포함)로 수정 위임")
    void updateExpense() throws Exception {
        given(expenseService.updateExpense(eq(10L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"categoryRowId":5,"amount":20000,"expenseType":"EXPENSE",
                 "splits":[{"categoryRowId":7,"amount":8000,"label":"커피","sortOrder":0},
                           {"categoryRowId":8,"amount":12000,"label":"밥","sortOrder":1}]}
                """;

        mockMvc.perform(put("/api/v1/expense/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(10));

        var captor = ArgumentCaptor.forClass(ExpenseServiceDto.UpdateCommand.class);
        verify(expenseService).updateExpense(eq(10L), eq(1L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(20000L);
        assertThat(captor.getValue().splits()).hasSize(2);
        assertThat(captor.getValue().splits().get(0).categoryRowId()).isEqualTo(7L);
        assertThat(captor.getValue().splits().get(0).amount()).isEqualTo(8000L);
    }

    @Test
    @DisplayName("PUT /expense/{id} — splits 미포함이면 null(분할 미변경) 로 위임")
    void updateExpenseWithoutSplits() throws Exception {
        given(expenseService.updateExpense(eq(10L), eq(1L), any())).willReturn(sampleInfo());

        mockMvc.perform(put("/api/v1/expense/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30000}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ExpenseServiceDto.UpdateCommand.class);
        verify(expenseService).updateExpense(eq(10L), eq(1L), captor.capture());
        assertThat(captor.getValue().splits()).isNull();
    }

    @Test
    @DisplayName("DELETE /expense/{id} — id·로그인 사용자로 삭제 위임")
    void deleteExpense() throws Exception {
        mockMvc.perform(delete("/api/v1/expense/{id}", 10L))
                .andExpect(status().isOk());

        verify(expenseService).deleteExpense(eq(10L), eq(1L));
    }

    @Test
    @DisplayName("GET /expenses/summary/daily — 날짜 파라미터로 일별 요약 위임")
    void getDailySummary() throws Exception {
        given(expenseService.getDailySummary(1L, LocalDate.of(2026, 7, 3)))
                .willReturn(new ExpenseServiceDto.DailySummary(LocalDate.of(2026, 7, 3), 0L, 15000L));

        mockMvc.perform(get("/api/v1/expenses/summary/daily").param("date", "2026-07-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(15000))
                .andExpect(jsonPath("$.data.totalIncome").value(0));

        verify(expenseService).getDailySummary(1L, LocalDate.of(2026, 7, 3));
    }

    @Test
    @DisplayName("GET /expenses/summary/range — 기간 파라미터로 범위 요약 위임")
    void getRangeSummary() throws Exception {
        given(expenseService.getRangeSummary(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .willReturn(new ExpenseServiceDto.RangeSummary(
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                        1000L, 5000L, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/expenses/summary/range")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIncome").value(1000))
                .andExpect(jsonPath("$.data.totalExpense").value(5000));

        verify(expenseService).getRangeSummary(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("GET /expenses/summary/trend — months 기본값 6 으로 추이 위임")
    void getMonthlyTrendDefault() throws Exception {
        given(expenseService.getMonthlyTrend(1L, 6)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/expenses/summary/trend"))
                .andExpect(status().isOk());

        verify(expenseService).getMonthlyTrend(1L, 6);
    }

    @Test
    @DisplayName("GET /expenses/summary/trend?months=3 — 지정 months 로 추이 위임")
    void getMonthlyTrendExplicit() throws Exception {
        given(expenseService.getMonthlyTrend(1L, 3)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/expenses/summary/trend").param("months", "3"))
                .andExpect(status().isOk());

        verify(expenseService).getMonthlyTrend(1L, 3);
    }

    @Test
    @DisplayName("GET /expenses/summary/by-merchant — 기간(선택) 매핑해 위임")
    void getMerchantSummary() throws Exception {
        given(expenseService.getMerchantSummary(1L, null, null))
                .willReturn(List.of(new ExpenseServiceDto.MerchantSummary("김밥천국", 15000L, 2)));

        mockMvc.perform(get("/api/v1/expenses/summary/by-merchant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchants[0].merchant").value("김밥천국"))
                .andExpect(jsonPath("$.data.merchants[0].count").value(2));

        verify(expenseService).getMerchantSummary(1L, null, null);
    }

    @Test
    @DisplayName("GET /expenses/summary/by-asset — 기간(선택) 매핑해 위임")
    void getAssetSummary() throws Exception {
        given(expenseService.getAssetSummary(1L, null, null))
                .willReturn(List.of(new ExpenseServiceDto.AssetSummary(2L, "현금", 15000L, 3)));

        mockMvc.perform(get("/api/v1/expenses/summary/by-asset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets[0].assetName").value("현금"));

        verify(expenseService).getAssetSummary(1L, null, null);
    }

    @Test
    @DisplayName("GET /expenses/summary/heatmap — 기간 파라미터로 히트맵 위임")
    void getHeatmap() throws Exception {
        given(expenseService.getHeatmap(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .willReturn(List.of(new ExpenseServiceDto.HeatmapCell(1, 12, 15000L)));

        mockMvc.perform(get("/api/v1/expenses/summary/heatmap")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells[0].totalAmount").value(15000));

        verify(expenseService).getHeatmap(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("GET /calendar/event/{eventId}/expenses — 이벤트 id 로 조회 위임")
    void getExpensesByCalendarEvent() throws Exception {
        given(expenseService.getExpensesByCalendarEvent(77L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/calendar/event/{eventId}/expenses", 77L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenses[0].rowId").value(10));

        verify(expenseService).getExpensesByCalendarEvent(77L);
    }

    @Test
    @DisplayName("GET /todo/{todoId}/expenses — 투두 id 로 조회 위임")
    void getExpensesByTodo() throws Exception {
        given(expenseService.getExpensesByTodo(88L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/todo/{todoId}/expenses", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenses[0].rowId").value(10));

        verify(expenseService).getExpensesByTodo(88L);
    }

    @Test
    @DisplayName("GET /expenses/search — 검색 조건을 SearchCommand 로 묶어 위임")
    void searchExpenses() throws Exception {
        given(expenseService.searchExpenses(any())).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/expenses/search")
                        .param("categoryId", "5")
                        .param("assetId", "2")
                        .param("expenseType", "EXPENSE")
                        .param("keyword", "점심")
                        .param("merchant", "김밥")
                        .param("minAmount", "1000")
                        .param("maxAmount", "50000")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenses[0].rowId").value(10));

        var captor = ArgumentCaptor.forClass(ExpenseServiceDto.SearchCommand.class);
        verify(expenseService).searchExpenses(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().categoryRowId()).isEqualTo(5L);
        assertThat(captor.getValue().assetRowId()).isEqualTo(2L);
        assertThat(captor.getValue().expenseType()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(captor.getValue().keyword()).isEqualTo("점심");
        assertThat(captor.getValue().merchant()).isEqualTo("김밥");
        assertThat(captor.getValue().minAmount()).isEqualTo(1000L);
        assertThat(captor.getValue().maxAmount()).isEqualTo(50000L);
        assertThat(captor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(captor.getValue().endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }
}
