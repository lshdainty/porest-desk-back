package com.porest.desk.calendar.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.UserCalendarService;
import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.calendar.type.CalendarRole;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserCalendar API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 * CRUD·가시성 토글에 더해 공유(멤버 조회·초대코드·참여·퇴출·권한변경) 엔드포인트까지 커버한다.
 */
@WebMvcTest(controllers = UserCalendarApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class UserCalendarApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserCalendarService userCalendarService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private UserCalendarServiceDto.CalendarInfo sampleInfo() {
        return new UserCalendarServiceDto.CalendarInfo(
                40L, 1L, "오너", "내캘린더", "#0f0", 0,
                true, true, "INVITE123", false, true, CalendarRole.OWNER, 1, null, null);
    }

    private UserCalendarServiceDto.MemberInfo sampleMember() {
        return new UserCalendarServiceDto.MemberInfo(
                50L, 2L, "멤버", "m@e.com", CalendarRole.EDIT, null);
    }

    @Test
    @DisplayName("POST /calendar/calendars — 로그인 사용자·바디로 createCalendar 위임")
    void createCalendar() throws Exception {
        given(userCalendarService.createCalendar(any())).willReturn(sampleInfo());

        String body = """
                {"calendarName":"내캘린더","color":"#0f0"}
                """;

        mockMvc.perform(post("/api/v1/calendar/calendars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40))
                .andExpect(jsonPath("$.data.calendarName").value("내캘린더"));

        var captor = ArgumentCaptor.forClass(UserCalendarServiceDto.CreateCommand.class);
        verify(userCalendarService).createCalendar(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().calendarName()).isEqualTo("내캘린더");
        assertThat(captor.getValue().color()).isEqualTo("#0f0");
    }

    @Test
    @DisplayName("GET /calendar/calendars — 로그인 사용자로 목록 조회")
    void getCalendars() throws Exception {
        given(userCalendarService.getCalendars(1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/calendar/calendars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calendars.length()").value(1))
                .andExpect(jsonPath("$.data.calendars[0].rowId").value(40));

        verify(userCalendarService).getCalendars(1L);
    }

    @Test
    @DisplayName("PUT /calendar/calendars/{id} — id·로그인 사용자·바디로 updateCalendar 위임")
    void updateCalendar() throws Exception {
        given(userCalendarService.updateCalendar(eq(40L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"calendarName":"수정캘린더","color":"#00f"}
                """;

        mockMvc.perform(put("/api/v1/calendar/calendars/{id}", 40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40));

        var captor = ArgumentCaptor.forClass(UserCalendarServiceDto.UpdateCommand.class);
        verify(userCalendarService).updateCalendar(eq(40L), eq(1L), captor.capture());
        assertThat(captor.getValue().calendarName()).isEqualTo("수정캘린더");
        assertThat(captor.getValue().color()).isEqualTo("#00f");
    }

    @Test
    @DisplayName("PATCH /calendar/calendars/{id}/visibility — id·로그인 사용자로 가시성 토글")
    void toggleVisibility() throws Exception {
        given(userCalendarService.toggleVisibility(40L, 1L)).willReturn(sampleInfo());

        mockMvc.perform(patch("/api/v1/calendar/calendars/{id}/visibility", 40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40));

        verify(userCalendarService).toggleVisibility(40L, 1L);
    }

    @Test
    @DisplayName("DELETE /calendar/calendars/{id} — id·로그인 사용자로 삭제 위임")
    void deleteCalendar() throws Exception {
        mockMvc.perform(delete("/api/v1/calendar/calendars/{id}", 40L))
                .andExpect(status().isOk());

        verify(userCalendarService).deleteCalendar(eq(40L), eq(1L));
    }

    @Test
    @DisplayName("GET /calendar/calendars/{id}/members — id·로그인 사용자로 멤버 목록 조회")
    void getMembers() throws Exception {
        given(userCalendarService.getMembers(40L, 1L)).willReturn(List.of(sampleMember()));

        mockMvc.perform(get("/api/v1/calendar/calendars/{id}/members", 40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].rowId").value(50))
                .andExpect(jsonPath("$.data.members[0].userName").value("멤버"));

        verify(userCalendarService).getMembers(40L, 1L);
    }

    @Test
    @DisplayName("PATCH /calendar/calendars/{id}/regenerate-invite-code — 새 초대코드 응답")
    void regenerateInviteCode() throws Exception {
        given(userCalendarService.regenerateInviteCode(40L, 1L)).willReturn("NEWCODE99");

        mockMvc.perform(patch("/api/v1/calendar/calendars/{id}/regenerate-invite-code", 40L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").value("NEWCODE99"));

        verify(userCalendarService).regenerateInviteCode(40L, 1L);
    }

    @Test
    @DisplayName("POST /calendar/calendars/join — 로그인 사용자·초대코드로 참여")
    void joinByInviteCode() throws Exception {
        given(userCalendarService.joinByInviteCode(eq(1L), eq("INVITE123"))).willReturn(sampleInfo());

        String body = """
                {"inviteCode":"INVITE123"}
                """;

        mockMvc.perform(post("/api/v1/calendar/calendars/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(40));

        verify(userCalendarService).joinByInviteCode(1L, "INVITE123");
    }

    @Test
    @DisplayName("DELETE /calendar/calendars/{id}/member/{memberId} — 캘린더·멤버·요청자로 퇴출 위임")
    void removeMember() throws Exception {
        mockMvc.perform(delete("/api/v1/calendar/calendars/{id}/member/{memberId}", 40L, 50L))
                .andExpect(status().isOk());

        verify(userCalendarService).removeMember(eq(40L), eq(50L), eq(1L));
    }

    @Test
    @DisplayName("PATCH /calendar/calendars/{id}/member/{memberId}/role — 권한·요청자로 역할 변경 위임")
    void changeMemberRole() throws Exception {
        String body = """
                {"permission":"EDIT"}
                """;

        mockMvc.perform(patch("/api/v1/calendar/calendars/{id}/member/{memberId}/role", 40L, 50L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(userCalendarService).changeMemberRole(eq(40L), eq(50L), eq(CalendarRole.EDIT), eq(1L));
    }
}
