package com.porest.desk.calendar.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.CalendarEventService;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.type.CalendarEventType;
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
 * CalendarEvent API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 경로/쿼리 매핑·바디 역직렬화·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = CalendarEventApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CalendarEventApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CalendarEventService calendarEventService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private CalendarEventServiceDto.EventInfo sampleInfo() {
        return new CalendarEventServiceDto.EventInfo(
                10L, 1L, "회의", "설명",
                CalendarEventType.WORK, "#fff",
                LocalDateTime.of(2026, 7, 3, 10, 0), LocalDateTime.of(2026, 7, 3, 11, 0),
                YNType.N, null, null, null, null, null, null, YNType.N,
                List.of(), 40L, "내캘린더", "#0f0", null, null);
    }

    @Test
    @DisplayName("POST /calendar/event — 로그인 사용자·바디로 createEvent 위임 + 응답 본문")
    void createEvent() throws Exception {
        given(calendarEventService.createEvent(any())).willReturn(sampleInfo());

        String body = """
                {"title":"회의","description":"설명","eventType":"WORK","color":"#fff",
                 "startDate":"2026-07-03T10:00:00","endDate":"2026-07-03T11:00:00",
                 "isAllDay":"N","reminderMinutes":[10,30],"calendarRowId":40}
                """;

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(10))
                .andExpect(jsonPath("$.data.title").value("회의"))
                .andExpect(jsonPath("$.data.calendarRowId").value(40));

        var captor = ArgumentCaptor.forClass(CalendarEventServiceDto.CreateCommand.class);
        verify(calendarEventService).createEvent(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().title()).isEqualTo("회의");
        assertThat(captor.getValue().eventType()).isEqualTo(CalendarEventType.WORK);
        assertThat(captor.getValue().isAllDay()).isEqualTo(YNType.N);
        assertThat(captor.getValue().reminderMinutes()).containsExactly(10, 30);
        assertThat(captor.getValue().calendarRowId()).isEqualTo(40L);
    }

    @Test
    @DisplayName("GET /calendar/events — 로그인 사용자·기간 쿼리로 getEvents 위임")
    void getEvents() throws Exception {
        given(calendarEventService.getEvents(eq(1L), any(), any()))
                .willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/calendar/events")
                        .param("startDate", "2026-07-01T00:00:00")
                        .param("endDate", "2026-07-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].rowId").value(10));

        verify(calendarEventService).getEvents(
                1L,
                LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                LocalDateTime.of(2026, 7, 31, 23, 59, 59));
    }

    @Test
    @DisplayName("PUT /calendar/event/{id} — id·로그인 사용자·바디로 updateEvent 위임")
    void updateEvent() throws Exception {
        given(calendarEventService.updateEvent(eq(10L), eq(1L), any()))
                .willReturn(sampleInfo());

        String body = """
                {"title":"수정회의","eventType":"PERSONAL","isAllDay":"Y"}
                """;

        mockMvc.perform(put("/api/v1/calendar/event/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(10));

        var captor = ArgumentCaptor.forClass(CalendarEventServiceDto.UpdateCommand.class);
        verify(calendarEventService).updateEvent(eq(10L), eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("수정회의");
        assertThat(captor.getValue().eventType()).isEqualTo(CalendarEventType.PERSONAL);
        assertThat(captor.getValue().isAllDay()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("DELETE /calendar/event/{id} — id·로그인 사용자로 삭제 위임")
    void deleteEvent() throws Exception {
        mockMvc.perform(delete("/api/v1/calendar/event/{id}", 10L))
                .andExpect(status().isOk());

        verify(calendarEventService).deleteEvent(eq(10L), eq(1L));
    }

    @Test
    @DisplayName("PUT /calendar/event/{id} — 숫자 아닌 경로변수는 400")
    void updateEvent_nonNumericId_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/calendar/event/{id}", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /calendar/event — 존재하지 않는 시작일(2026-02-30)은 400 (종전엔 500)")
    void createEventRejectsImpossibleStartDate() throws Exception {
        String body = """
                {"title":"회의","eventType":"WORK",
                 "startDate":"2026-02-30T09:00:00","endDate":"2026-07-03T10:00:00","isAllDay":"N"}
                """;

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(calendarEventService, never()).createEvent(any());
    }

    @Test
    @DisplayName("POST /calendar/event — 제목 201자는 400 (종전엔 DB 제약에 걸려 500)")
    void createEventRejectsLongTitle() throws Exception {
        String body = """
                {"title":"%s","eventType":"PERSONAL",
                 "startDate":"2026-07-03T09:00:00","endDate":"2026-07-03T10:00:00","isAllDay":"N"}
                """.formatted("가".repeat(201));

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(calendarEventService, never()).createEvent(any());
    }

    @Test
    @DisplayName("POST /calendar/event — 제목 200자(경계)는 통과")
    void createEventAcceptsTitleAtLimit() throws Exception {
        given(calendarEventService.createEvent(any())).willReturn(sampleInfo());

        String body = """
                {"title":"%s","eventType":"PERSONAL",
                 "startDate":"2026-07-03T09:00:00","endDate":"2026-07-03T10:00:00","isAllDay":"N"}
                """.formatted("가".repeat(200));

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(calendarEventService).createEvent(any());
    }

    @Test
    @DisplayName("PUT /calendar/event/{id} — 제목 201자는 400")
    void updateEventRejectsLongTitle() throws Exception {
        String body = """
                {"title":"%s","eventType":"PERSONAL"}
                """.formatted("가".repeat(201));

        mockMvc.perform(put("/api/v1/calendar/event/{id}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(calendarEventService, never()).updateEvent(any(Long.class), any(Long.class), any());
    }

    @Test
    @DisplayName("POST /calendar/event — 설명 10,001자는 400 (공통 상한 10,000)")
    void createEventRejectsOversizedDescription() throws Exception {
        String body = "{\"title\":\"회의\",\"eventType\":\"WORK\","
                + "\"startDate\":\"2026-07-03T09:00:00\",\"endDate\":\"2026-07-03T10:00:00\","
                + "\"isAllDay\":\"N\",\"description\":\"" + "가".repeat(10_001) + "\"}";

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(calendarEventService, never()).createEvent(any());
    }

    @Test
    @DisplayName("POST /calendar/event — 설명 10,000자(경계)는 통과")
    void createEventAcceptsDescriptionAtLimit() throws Exception {
        given(calendarEventService.createEvent(any())).willReturn(sampleInfo());

        String body = "{\"title\":\"회의\",\"eventType\":\"WORK\","
                + "\"startDate\":\"2026-07-03T09:00:00\",\"endDate\":\"2026-07-03T10:00:00\","
                + "\"isAllDay\":\"N\",\"description\":\"" + "가".repeat(10_000) + "\"}";

        mockMvc.perform(post("/api/v1/calendar/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(calendarEventService).createEvent(any());
    }
}
