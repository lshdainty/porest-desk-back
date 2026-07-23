package com.porest.desk.todo.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.todo.service.TodoTagService;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
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
 * TodoTag API 슬라이스 테스트 — 매핑·바디 역직렬화·로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = TodoTagApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class TodoTagApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TodoTagService todoTagService;
    @MockitoBean private MessageResolver messageResolver;

    private TodoTagServiceDto.TagInfo sampleTag() {
        return new TodoTagServiceDto.TagInfo(100L, 1L, "긴급", "#ff0000", null, null, 0L);
    }

    @Test
    @DisplayName("POST /todo-tag — 로그인 사용자·바디로 생성 위임")
    void createTag() throws Exception {
        given(todoTagService.createTag(any())).willReturn(sampleTag());

        mockMvc.perform(post("/api/v1/todo-tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"긴급\",\"color\":\"#ff0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.tagName").value("긴급"));

        var captor = ArgumentCaptor.forClass(TodoTagServiceDto.CreateCommand.class);
        verify(todoTagService).createTag(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().tagName()).isEqualTo("긴급");
        assertThat(captor.getValue().color()).isEqualTo("#ff0000");
    }

    @Test
    @DisplayName("GET /todo-tags — 로그인 사용자로 목록 조회")
    void getTags() throws Exception {
        given(todoTagService.getTags(1L)).willReturn(List.of(sampleTag()));

        mockMvc.perform(get("/api/v1/todo-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].rowId").value(100));

        verify(todoTagService).getTags(1L);
    }

    @Test
    @DisplayName("PUT /todo-tag/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateTag() throws Exception {
        given(todoTagService.updateTag(eq(100L), eq(1L), any())).willReturn(sampleTag());

        mockMvc.perform(put("/api/v1/todo-tag/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"보통\",\"color\":\"#00ff00\"}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TodoTagServiceDto.UpdateCommand.class);
        verify(todoTagService).updateTag(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().tagName()).isEqualTo("보통");
        assertThat(captor.getValue().color()).isEqualTo("#00ff00");
    }

    @Test
    @DisplayName("DELETE /todo-tag/{id} — id·로그인 사용자로 삭제 위임")
    void deleteTag() throws Exception {
        mockMvc.perform(delete("/api/v1/todo-tag/{id}", 100L))
                .andExpect(status().isOk());

        verify(todoTagService).deleteTag(100L, 1L);
    }
}
