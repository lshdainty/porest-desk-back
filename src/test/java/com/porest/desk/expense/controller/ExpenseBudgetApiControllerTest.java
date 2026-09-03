package com.porest.desk.expense.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.expense.service.ExpenseBudgetService;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExpenseBudget API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = ExpenseBudgetApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ExpenseBudgetApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExpenseBudgetService expenseBudgetService;
    @MockitoBean private MessageResolver messageResolver;

    private ExpenseBudgetServiceDto.BudgetInfo sampleInfo() {
        return new ExpenseBudgetServiceDto.BudgetInfo(
                50L, 1L, 5L, "식비", 300000L, 2026, 7,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    @DisplayName("POST /expense/budget — 로그인 사용자·바디로 예산 생성 위임")
    void createBudget() throws Exception {
        given(expenseBudgetService.createBudget(any())).willReturn(sampleInfo());

        String body = """
                {"categoryRowId":5,"budgetAmount":300000,"budgetYear":2026,"budgetMonth":7}
                """;

        mockMvc.perform(post("/api/v1/expense/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(50))
                .andExpect(jsonPath("$.data.budgetAmount").value(300000));

        var captor = ArgumentCaptor.forClass(ExpenseBudgetServiceDto.CreateCommand.class);
        verify(expenseBudgetService).createBudget(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().categoryRowId()).isEqualTo(5L);
        assertThat(captor.getValue().budgetAmount()).isEqualTo(300000L);
        assertThat(captor.getValue().budgetYear()).isEqualTo(2026);
        assertThat(captor.getValue().budgetMonth()).isEqualTo(7);
    }

    @Test
    @DisplayName("GET /expense/budgets — year·month 파라미터 매핑해 조회 위임")
    void getBudgets() throws Exception {
        given(expenseBudgetService.getBudgets(1L, 2026, 7)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/expense/budgets")
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgets[0].rowId").value(50));

        verify(expenseBudgetService).getBudgets(1L, 2026, 7);
    }

    @Test
    @DisplayName("GET /expense/budgets — year·month 미지정이면 null 로 위임")
    void getBudgetsNoParams() throws Exception {
        given(expenseBudgetService.getBudgets(1L, null, null)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/expense/budgets"))
                .andExpect(status().isOk());

        verify(expenseBudgetService).getBudgets(1L, null, null);
    }

    @Test
    @DisplayName("GET /expense/budgets/compliance — months 기본값 6 으로 위임")
    void getComplianceDefault() throws Exception {
        given(expenseBudgetService.getCompliance(1L, 6))
                .willReturn(List.of(new ExpenseBudgetServiceDto.ComplianceMonth(2026, 7, 300000L, 150000L, 50.0)));

        mockMvc.perform(get("/api/v1/expense/budgets/compliance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months[0].compliancePercent").value(50.0));

        verify(expenseBudgetService).getCompliance(1L, 6);
    }

    @Test
    @DisplayName("GET /expense/budgets/compliance?months=12 — 지정 months 로 위임")
    void getComplianceExplicit() throws Exception {
        given(expenseBudgetService.getCompliance(1L, 12)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/expense/budgets/compliance").param("months", "12"))
                .andExpect(status().isOk());

        verify(expenseBudgetService).getCompliance(1L, 12);
    }

    @Test
    @DisplayName("PUT /expense/budget/{id} — id·로그인 사용자·바디로 예산 수정 위임")
    void updateBudget() throws Exception {
        given(expenseBudgetService.updateBudget(eq(50L), eq(1L), any())).willReturn(sampleInfo());

        mockMvc.perform(put("/api/v1/expense/budget/{id}", 50L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetAmount\":500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(50));

        var captor = ArgumentCaptor.forClass(ExpenseBudgetServiceDto.UpdateCommand.class);
        verify(expenseBudgetService).updateBudget(eq(50L), eq(1L), captor.capture());
        assertThat(captor.getValue().budgetAmount()).isEqualTo(500000L);
    }

    @Test
    @DisplayName("DELETE /expense/budget/{id} — id·로그인 사용자로 삭제 위임")
    void deleteBudget() throws Exception {
        mockMvc.perform(delete("/api/v1/expense/budget/{id}", 50L))
                .andExpect(status().isOk());

        verify(expenseBudgetService).deleteBudget(eq(50L), eq(1L));
    }

    // === 금액 하한·상한 (QA #47 #48) ===

    @Test
    @DisplayName("POST /expense/budget — 999억은 400 (거래와 같은 100억 상한)")
    void createRejectsOverLimit() throws Exception {
        mockMvc.perform(post("/api/v1/expense/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryRowId\":5,\"budgetAmount\":99999999999,\"budgetYear\":2026,\"budgetMonth\":7}"))
                .andExpect(status().isBadRequest());

        verify(expenseBudgetService, never()).createBudget(any());
    }

    @Test
    @DisplayName("POST /expense/budget — 정확히 100억(경계)은 통과")
    void createAcceptsAmountAtLimit() throws Exception {
        given(expenseBudgetService.createBudget(any())).willReturn(sampleInfo());

        mockMvc.perform(post("/api/v1/expense/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryRowId\":5,\"budgetAmount\":10000000000,\"budgetYear\":2026,\"budgetMonth\":7}"))
                .andExpect(status().isOk());

        verify(expenseBudgetService).createBudget(any());
    }

    @Test
    @DisplayName("POST /expense/budget — 음수는 400 (종전엔 서비스까지 들어가서 막혔다)")
    void createRejectsNegative() throws Exception {
        mockMvc.perform(post("/api/v1/expense/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryRowId\":5,\"budgetAmount\":-1000,\"budgetYear\":2026,\"budgetMonth\":7}"))
                .andExpect(status().isBadRequest());

        verify(expenseBudgetService, never()).createBudget(any());
    }

    @Test
    @DisplayName("PUT /expense/budget/{id} — 999억은 400")
    void updateRejectsOverLimit() throws Exception {
        mockMvc.perform(put("/api/v1/expense/budget/{id}", 50L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetAmount\":99999999999}"))
                .andExpect(status().isBadRequest());

        verify(expenseBudgetService, never()).updateBudget(any(Long.class), any(Long.class), any());
    }
}
