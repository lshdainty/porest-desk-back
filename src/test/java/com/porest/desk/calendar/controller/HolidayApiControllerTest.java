package com.porest.desk.calendar.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.HolidayService;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.calendar.type.HolidayType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Holiday API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 *
 * <p>공휴일은 스케줄러가 외부 소스와 맞추는 조회 전용 리소스라 쓰기 엔드포인트가 없어야 한다.
 */
@WebMvcTest(controllers = HolidayApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class HolidayApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HolidayService holidayService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private HolidayServiceDto.HolidayInfo sampleInfo() {
        return new HolidayServiceDto.HolidayInfo(
                10L, LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("GET /holidays — 기간 파라미터로 getHolidays 위임 + 목록 매핑")
    void getHolidays() throws Exception {
        given(holidayService.getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/holidays")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holidays[0].rowId").value(10))
                .andExpect(jsonPath("$.data.holidays[0].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data.holidays[0].holidayName").value("신정"))
                .andExpect(jsonPath("$.data.holidays[0].holidayType").value("PUBLIC"));

        verify(holidayService).getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("GET /holidays — 공휴일이 없으면 빈 배열을 반환한다")
    void getHolidaysEmpty() throws Exception {
        given(holidayService.getHolidays(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/holidays")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holidays").isArray())
                .andExpect(jsonPath("$.data.holidays").isEmpty());
    }

    @Test
    @DisplayName("GET /holidays — 기간 파라미터가 없으면 400")
    void getHolidaysRequiresDateParams() throws Exception {
        mockMvc.perform(get("/api/v1/holidays"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("공휴일은 자동 동기화가 유일한 쓰기 경로다 — 쓰기 엔드포인트가 없어야 한다")
    void writeEndpointsAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/holiday")
                        .contentType("application/json")
                        .content("{\"holidayDate\":\"2026-01-01\",\"holidayName\":\"신정\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/holiday/{id}", 10L)
                        .contentType("application/json")
                        .content("{\"holidayName\":\"신정\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/holiday/{id}", 10L))
                .andExpect(status().isNotFound());
    }
}
