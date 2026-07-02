package com.porest.desk.expense.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.expense.service.ExpenseSplitService;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExpenseSplit API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = ExpenseSplitApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ExpenseSplitApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExpenseSplitService expenseSplitService;
    @MockitoBean private MessageResolver messageResolver;

    private ExpenseSplitServiceDto.SplitInfo sampleInfo() {
        return new ExpenseSplitServiceDto.SplitInfo(
                200L, 10L, 7L, "커피", 8000L, "커피값", 0,
                LocalDateTime.of(2026, 7, 3, 12, 0), LocalDateTime.of(2026, 7, 3, 12, 0));
    }

    @Test
    @DisplayName("GET /expense/{expenseId}/splits — expenseId·로그인 사용자로 분할 조회 위임")
    void getSplits() throws Exception {
        given(expenseSplitService.getSplits(10L, 1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/expense/{expenseId}/splits", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.splits[0].rowId").value(200))
                .andExpect(jsonPath("$.data.splits[0].amount").value(8000));

        verify(expenseSplitService).getSplits(10L, 1L);
    }

    @Test
    @DisplayName("PUT /expense/{expenseId}/splits — 바디의 분할 리스트를 ReplaceCommand 로 묶어 위임")
    void replaceSplits() throws Exception {
        given(expenseSplitService.replaceSplits(any())).willReturn(List.of(sampleInfo()));

        String body = """
                {"splits":[{"categoryRowId":7,"amount":8000,"label":"커피","sortOrder":0},
                           {"categoryRowId":8,"amount":12000,"label":"밥","sortOrder":1}]}
                """;

        mockMvc.perform(put("/api/v1/expense/{expenseId}/splits", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.splits[0].rowId").value(200));

        var captor = ArgumentCaptor.forClass(ExpenseSplitServiceDto.ReplaceCommand.class);
        verify(expenseSplitService).replaceSplits(captor.capture());
        assertThat(captor.getValue().expenseRowId()).isEqualTo(10L);
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().splits()).hasSize(2);
        assertThat(captor.getValue().splits().get(0).categoryRowId()).isEqualTo(7L);
        assertThat(captor.getValue().splits().get(1).amount()).isEqualTo(12000L);
    }

    @Test
    @DisplayName("PUT /expense/{expenseId}/splits — splits 미포함이면 빈 리스트로 위임")
    void replaceSplitsEmpty() throws Exception {
        given(expenseSplitService.replaceSplits(any())).willReturn(List.of());

        mockMvc.perform(put("/api/v1/expense/{expenseId}/splits", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ExpenseSplitServiceDto.ReplaceCommand.class);
        verify(expenseSplitService).replaceSplits(captor.capture());
        assertThat(captor.getValue().splits()).isEmpty();
    }

    @Test
    @DisplayName("DELETE /expense/{expenseId}/splits — expenseId·로그인 사용자로 전체 삭제 위임")
    void deleteAllSplits() throws Exception {
        mockMvc.perform(delete("/api/v1/expense/{expenseId}/splits", 10L))
                .andExpect(status().isOk());

        verify(expenseSplitService).deleteAllSplits(eq(10L), eq(1L));
    }
}
