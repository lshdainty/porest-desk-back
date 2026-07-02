package com.porest.desk.calendar.controller;

import com.porest.core.type.YNType;
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
 * 서비스는 mock — 매핑·바디 역직렬화(enum 포함)·기간 파라미터 매핑·응답 본문을 검증한다.
 *
 * <p>주의: {@code HolidayApiDto} 는 Jackson2 의 {@code @JsonNaming(SnakeCaseStrategy)} 를 달고 있으나,
 * 런타임(Spring Boot 4 → Jackson 3)에서는 해당 애노테이션이 무시되어 실제 직렬화/역직렬화는 camelCase 로
 * 동작한다. 따라서 본 테스트는 실제 동작(camelCase)을 기준으로 검증한다.
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
                10L, LocalDate.of(2026, 1, 1), "신정", HolidayType.PUBLIC, YNType.N,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("POST /holiday — 바디로 createHoliday 위임 + 응답 매핑")
    void createHoliday() throws Exception {
        given(holidayService.createHoliday(any())).willReturn(sampleInfo());

        String body = """
                {"holidayDate":"2026-01-01","holidayName":"신정",
                 "holidayType":"PUBLIC","isRecurring":"Y"}
                """;

        mockMvc.perform(post("/api/v1/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(10))
                .andExpect(jsonPath("$.data.holidayName").value("신정"))
                .andExpect(jsonPath("$.data.holidayType").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isRecurring").value("N"));

        ArgumentCaptor<HolidayServiceDto.CreateCommand> captor =
                ArgumentCaptor.forClass(HolidayServiceDto.CreateCommand.class);
        verify(holidayService).createHoliday(captor.capture());
        assertThat(captor.getValue().holidayDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(captor.getValue().holidayName()).isEqualTo("신정");
        assertThat(captor.getValue().holidayType()).isEqualTo(HolidayType.PUBLIC);
        assertThat(captor.getValue().isRecurring()).isEqualTo(YNType.Y);
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
                .andExpect(jsonPath("$.data.holidays[0].isRecurring").value("N"));

        verify(holidayService).getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("PUT /holiday/{id} — id·바디로 updateHoliday 위임")
    void updateHoliday() throws Exception {
        given(holidayService.updateHoliday(eq(10L), any())).willReturn(sampleInfo());

        String body = """
                {"holidayDate":"2026-01-01","holidayName":"신정(수정)",
                 "holidayType":"CUSTOM","isRecurring":"N"}
                """;

        mockMvc.perform(put("/api/v1/holiday/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<HolidayServiceDto.UpdateCommand> captor =
                ArgumentCaptor.forClass(HolidayServiceDto.UpdateCommand.class);
        verify(holidayService).updateHoliday(eq(10L), captor.capture());
        assertThat(captor.getValue().holidayName()).isEqualTo("신정(수정)");
        assertThat(captor.getValue().holidayType()).isEqualTo(HolidayType.CUSTOM);
        assertThat(captor.getValue().isRecurring()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("DELETE /holiday/{id} — id 로 deleteHoliday 위임")
    void deleteHoliday() throws Exception {
        mockMvc.perform(delete("/api/v1/holiday/{id}", 10L))
                .andExpect(status().isOk());

        verify(holidayService).deleteHoliday(10L);
    }
}
