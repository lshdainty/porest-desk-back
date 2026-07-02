package com.porest.desk.calendar.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.CalendarAggregateService;
import com.porest.desk.calendar.service.dto.CalendarAggregateDto;
import com.porest.desk.common.config.web.WebConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CalendarAggregate API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 * 기간(LocalDate) 쿼리 매핑과 events/todos/expenses 집계 응답 형태를 확인한다.
 */
@WebMvcTest(controllers = CalendarAggregateApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CalendarAggregateApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CalendarAggregateService calendarAggregateService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("GET /calendar/aggregate — 로그인 사용자·기간 쿼리로 getAggregateData 위임 + 집계 응답")
    void getAggregateData() throws Exception {
        given(calendarAggregateService.getAggregateData(1L,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .willReturn(new CalendarAggregateDto.AggregateData(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/calendar/aggregate")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(0))
                .andExpect(jsonPath("$.data.todos.length()").value(0))
                .andExpect(jsonPath("$.data.expenses.length()").value(0));

        verify(calendarAggregateService).getAggregateData(
                1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("GET /calendar/aggregate — 날짜 형식이 잘못되면 400")
    void getAggregateData_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/calendar/aggregate")
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-07-31")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
