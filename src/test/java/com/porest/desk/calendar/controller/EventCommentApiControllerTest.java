package com.porest.desk.calendar.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.EventCommentService;
import com.porest.desk.calendar.service.dto.EventCommentServiceDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EventComment API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 * 경로변수(eventId/commentId) 매핑과 CreateCommand/UpdateCommand 조립을 확인한다.
 */
@WebMvcTest(controllers = EventCommentApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class EventCommentApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EventCommentService eventCommentService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private EventCommentServiceDto.CommentInfo sampleInfo() {
        return new EventCommentServiceDto.CommentInfo(
                20L, 5L, 1L, "테스터", null, "댓글내용", null, null);
    }

    @Test
    @DisplayName("POST /calendar/event/{eventId}/comment — eventId·로그인 사용자·바디로 createComment 위임")
    void createComment() throws Exception {
        given(eventCommentService.createComment(any())).willReturn(sampleInfo());

        String body = """
                {"parentRowId":7,"content":"댓글내용"}
                """;

        mockMvc.perform(post("/api/v1/calendar/event/{eventId}/comment", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(20))
                .andExpect(jsonPath("$.data.content").value("댓글내용"));

        var captor = ArgumentCaptor.forClass(EventCommentServiceDto.CreateCommand.class);
        verify(eventCommentService).createComment(captor.capture());
        assertThat(captor.getValue().eventRowId()).isEqualTo(5L);
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().parentRowId()).isEqualTo(7L);
        assertThat(captor.getValue().content()).isEqualTo("댓글내용");
    }

    @Test
    @DisplayName("GET /calendar/event/{eventId}/comments — eventId·로그인 사용자로 목록 조회")
    void getComments() throws Exception {
        given(eventCommentService.getComments(5L, 1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/calendar/event/{eventId}/comments", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments.length()").value(1))
                .andExpect(jsonPath("$.data.comments[0].rowId").value(20));

        verify(eventCommentService).getComments(5L, 1L);
    }

    @Test
    @DisplayName("PUT /calendar/comment/{commentId} — commentId·로그인 사용자·바디로 updateComment 위임")
    void updateComment() throws Exception {
        given(eventCommentService.updateComment(eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"content":"수정된댓글"}
                """;

        mockMvc.perform(put("/api/v1/calendar/comment/{commentId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(20));

        var captor = ArgumentCaptor.forClass(EventCommentServiceDto.UpdateCommand.class);
        verify(eventCommentService).updateComment(eq(1L), captor.capture());
        assertThat(captor.getValue().commentRowId()).isEqualTo(20L);
        assertThat(captor.getValue().content()).isEqualTo("수정된댓글");
    }

    @Test
    @DisplayName("DELETE /calendar/comment/{commentId} — commentId·로그인 사용자로 삭제 위임")
    void deleteComment() throws Exception {
        mockMvc.perform(delete("/api/v1/calendar/comment/{commentId}", 20L))
                .andExpect(status().isOk());

        verify(eventCommentService).deleteComment(eq(20L), eq(1L));
    }
}
