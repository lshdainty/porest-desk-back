package com.porest.desk.expense.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.expense.service.RecurringTransactionService;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RecurringTransaction API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = RecurringTransactionApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class RecurringTransactionApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RecurringTransactionService recurringTransactionService;
    @MockitoBean private MessageResolver messageResolver;

    private RecurringTransactionServiceDto.RecurringInfo sampleInfo() {
        return new RecurringTransactionServiceDto.RecurringInfo(
                40L, 1L, 5L, "구독", 2L, "카드", null,
                ExpenseType.EXPENSE, 9900L, "넷플릭스", "넷플릭스", "CARD",
                RecurringFrequency.MONTHLY, 1, null, 15,
                LocalTime.of(9, 0),
                LocalDate.of(2026, 7, 15), null, null, 3,
                LocalDate.of(2026, 8, 15), LocalDateTime.of(2026, 7, 15, 0, 0),
                YNType.Y, true, false,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    @DisplayName("POST /recurring-transaction — 로그인 사용자·바디로 반복거래 생성 위임")
    void createRecurring() throws Exception {
        given(recurringTransactionService.createRecurring(any())).willReturn(sampleInfo());

        String body = """
                {"categoryRowId":5,"assetRowId":2,"expenseType":"EXPENSE","amount":9900,
                 "description":"넷플릭스","merchant":"넷플릭스","paymentMethod":"CARD",
                 "frequency":"MONTHLY","intervalValue":1,"dayOfMonth":15,
                 "startDate":"2026-07-15","maxOccurrences":3,"autoLog":true,"notifyDayBefore":false}
                """;

        mockMvc.perform(post("/api/v1/recurring-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40))
                .andExpect(jsonPath("$.data.frequency").value("MONTHLY"));

        var captor = ArgumentCaptor.forClass(RecurringTransactionServiceDto.CreateCommand.class);
        verify(recurringTransactionService).createRecurring(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().categoryRowId()).isEqualTo(5L);
        assertThat(captor.getValue().amount()).isEqualTo(9900L);
        assertThat(captor.getValue().frequency()).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(captor.getValue().dayOfMonth()).isEqualTo(15);
        assertThat(captor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(captor.getValue().autoLog()).isTrue();
        assertThat(captor.getValue().notifyDayBefore()).isFalse();
    }

    @Test
    @DisplayName("GET /recurring-transactions — upcoming·limit 매핑해 조회 위임")
    void getRecurringsWithParams() throws Exception {
        given(recurringTransactionService.getRecurrings(1L, true, 5)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/recurring-transactions")
                        .param("upcoming", "true")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurringTransactions[0].rowId").value(40));

        verify(recurringTransactionService).getRecurrings(1L, true, 5);
    }

    @Test
    @DisplayName("GET /recurring-transactions — 파라미터 미지정이면 upcoming=false·limit=null 로 위임")
    void getRecurringsNoParams() throws Exception {
        given(recurringTransactionService.getRecurrings(eq(1L), eq(false), isNull()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/recurring-transactions"))
                .andExpect(status().isOk());

        verify(recurringTransactionService).getRecurrings(eq(1L), eq(false), isNull());
    }

    @Test
    @DisplayName("PUT /recurring-transaction/{id} — id·로그인 사용자·바디로 수정 위임")
    void updateRecurring() throws Exception {
        given(recurringTransactionService.updateRecurring(eq(40L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"categoryRowId":5,"assetRowId":2,"expenseType":"EXPENSE","amount":12000,
                 "description":"인상","merchant":"넷플릭스","paymentMethod":"CARD",
                 "frequency":"MONTHLY","intervalValue":1,"dayOfMonth":20,
                 "startDate":"2026-07-20","autoLog":false,"notifyDayBefore":true}
                """;

        mockMvc.perform(put("/api/v1/recurring-transaction/{id}", 40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40));

        var captor = ArgumentCaptor.forClass(RecurringTransactionServiceDto.UpdateCommand.class);
        verify(recurringTransactionService).updateRecurring(eq(40L), eq(1L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(12000L);
        assertThat(captor.getValue().dayOfMonth()).isEqualTo(20);
        assertThat(captor.getValue().frequency()).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(captor.getValue().notifyDayBefore()).isTrue();
    }

    @Test
    @DisplayName("DELETE /recurring-transaction/{id} — id·로그인 사용자로 삭제 위임")
    void deleteRecurring() throws Exception {
        mockMvc.perform(delete("/api/v1/recurring-transaction/{id}", 40L))
                .andExpect(status().isOk());

        verify(recurringTransactionService).deleteRecurring(eq(40L), eq(1L));
    }

    @Test
    @DisplayName("PATCH /recurring-transaction/{id}/toggle — id·로그인 사용자로 활성 토글 위임")
    void toggleActive() throws Exception {
        given(recurringTransactionService.toggleActive(40L, 1L)).willReturn(sampleInfo());

        mockMvc.perform(patch("/api/v1/recurring-transaction/{id}/toggle", 40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40))
                .andExpect(jsonPath("$.data.isActive").value("Y"));

        verify(recurringTransactionService).toggleActive(40L, 1L);
    }

    // === 금액 상한·하한 (QA #54) ===

    private static String createBody(String amount) {
        return "{\"categoryRowId\":5,\"expenseType\":\"EXPENSE\",\"amount\":" + amount
                + ",\"frequency\":\"MONTHLY\",\"startDate\":\"2026-07-15\"}";
    }

    @Test
    @DisplayName("POST /recurring-transaction — 999억은 400 (거래 상한을 우회하는 경로였다)")
    void createRejectsOverLimit() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("99999999999")))
                .andExpect(status().isBadRequest());

        verify(recurringTransactionService, never()).createRecurring(any());
    }

    @Test
    @DisplayName("POST /recurring-transaction — 정확히 100억(경계)은 통과")
    void createAcceptsAmountAtLimit() throws Exception {
        given(recurringTransactionService.createRecurring(any())).willReturn(sampleInfo());

        mockMvc.perform(post("/api/v1/recurring-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("10000000000")))
                .andExpect(status().isOk());

        verify(recurringTransactionService).createRecurring(any());
    }

    @Test
    @DisplayName("POST /recurring-transaction — 음수는 400")
    void createRejectsNegative() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("-5")))
                .andExpect(status().isBadRequest());

        verify(recurringTransactionService, never()).createRecurring(any());
    }

    @Test
    @DisplayName("POST /recurring-transaction — 거래처 101자는 400 (컬럼 varchar(100))")
    void createRejectsLongMerchant() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryRowId\":5,\"expenseType\":\"EXPENSE\",\"amount\":9900,"
                                + "\"merchant\":\"" + "가".repeat(101) + "\","
                                + "\"frequency\":\"MONTHLY\",\"startDate\":\"2026-07-15\"}"))
                .andExpect(status().isBadRequest());

        verify(recurringTransactionService, never()).createRecurring(any());
    }

    @Test
    @DisplayName("PUT /recurring-transaction/{id} — 999억은 400")
    void updateRejectsOverLimit() throws Exception {
        mockMvc.perform(put("/api/v1/recurring-transaction/{id}", 40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999999999}"))
                .andExpect(status().isBadRequest());

        verify(recurringTransactionService, never()).updateRecurring(any(Long.class), any(Long.class), any());
    }
}
