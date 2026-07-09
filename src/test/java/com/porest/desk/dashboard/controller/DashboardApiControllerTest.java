package com.porest.desk.dashboard.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.dashboard.service.DashboardService;
import com.porest.desk.dashboard.service.dto.DashboardServiceDto;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 매핑·바디 역직렬화·로그인 사용자 위임·응답 본문을 검증한다.
 */
@WebMvcTest(controllers = DashboardApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class DashboardApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DashboardService dashboardService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private DashboardServiceDto.DashboardSummary sampleSummary() {
        return new DashboardServiceDto.DashboardSummary(
                new DashboardServiceDto.TodoSummary(5, 1, 2, 2, 1, 3),
                new DashboardServiceDto.CalendarSummary(1, 3, LocalDate.of(2026, 7, 10)),
                new DashboardServiceDto.ExpenseSummary(100, 50, 1000, 500),
                new DashboardServiceDto.MemoSummary(4, 1, "최근 메모"),
                List.of(new DashboardServiceDto.UpcomingEvent(
                        11L, "회의", "MEETING", "#fff", LocalDateTime.of(2026, 7, 10, 9, 0), 7)),
                List.of(new DashboardServiceDto.RecentTodo(
                        22L, "할 일", "HIGH", "PENDING", LocalDate.of(2026, 7, 5))),
                List.of(new DashboardServiceDto.DailyExpenseTrend(LocalDate.of(2026, 7, 1), 100, 50)));
    }

    @Test
    @DisplayName("GET /dashboard/summary — 로그인 사용자로 요약 조회 + 응답 매핑")
    void getDashboardSummary() throws Exception {
        given(dashboardService.getDashboardSummary(1L)).willReturn(sampleSummary());

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoSummary.totalCount").value(5))
                .andExpect(jsonPath("$.data.todoSummary.overDueCount").value(3))
                .andExpect(jsonPath("$.data.expenseSummary.monthlyIncome").value(1000))
                .andExpect(jsonPath("$.data.upcomingEvents[0].title").value("회의"))
                .andExpect(jsonPath("$.data.recentTodos[0].rowId").value(22));

        verify(dashboardService).getDashboardSummary(1L);
    }

    @Test
    @DisplayName("GET /dashboard/layout — 로그인 사용자로 레이아웃 조회")
    void getDashboardLayout() throws Exception {
        given(dashboardService.getDashboardLayout(1L)).willReturn("[{\"i\":\"todo\"}]");

        mockMvc.perform(get("/api/v1/dashboard/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dashboard").value("[{\"i\":\"todo\"}]"));

        verify(dashboardService).getDashboardLayout(1L);
    }

    @Test
    @DisplayName("PATCH /dashboard/layout — 로그인 사용자·바디로 레이아웃 갱신 위임")
    void updateDashboardLayout() throws Exception {
        given(dashboardService.updateDashboardLayout(1L, "[{\"i\":\"memo\"}]"))
                .willReturn("[{\"i\":\"memo\"}]");

        String body = """
                {"dashboard":"[{\\"i\\":\\"memo\\"}]"}
                """;

        mockMvc.perform(patch("/api/v1/dashboard/layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dashboard").value("[{\"i\":\"memo\"}]"));

        verify(dashboardService).updateDashboardLayout(1L, "[{\"i\":\"memo\"}]");
    }
}
