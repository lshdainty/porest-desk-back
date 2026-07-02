package com.porest.desk.todo.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.todo.service.TodoProjectService;
import com.porest.desk.todo.service.dto.TodoProjectServiceDto;
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
 * TodoProject API 슬라이스 테스트 — 매핑·바디 역직렬화·로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = TodoProjectApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class TodoProjectApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TodoProjectService todoProjectService;
    @MockitoBean private MessageResolver messageResolver;

    private TodoProjectServiceDto.ProjectInfo sampleProject() {
        return new TodoProjectServiceDto.ProjectInfo(
                100L, 1L, "프로젝트", "설명", "#ffffff", "folder", 0, null, null);
    }

    @Test
    @DisplayName("POST /todo-project — 로그인 사용자·바디로 생성 위임")
    void createProject() throws Exception {
        given(todoProjectService.createProject(any())).willReturn(sampleProject());

        String body = """
                {"projectName":"프로젝트","description":"설명","color":"#ffffff","icon":"folder"}
                """;

        mockMvc.perform(post("/api/v1/todo-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.projectName").value("프로젝트"));

        var captor = ArgumentCaptor.forClass(TodoProjectServiceDto.CreateCommand.class);
        verify(todoProjectService).createProject(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().projectName()).isEqualTo("프로젝트");
        assertThat(captor.getValue().color()).isEqualTo("#ffffff");
        assertThat(captor.getValue().icon()).isEqualTo("folder");
    }

    @Test
    @DisplayName("GET /todo-projects — 로그인 사용자로 목록 조회")
    void getProjects() throws Exception {
        given(todoProjectService.getProjects(1L)).willReturn(List.of(sampleProject()));

        mockMvc.perform(get("/api/v1/todo-projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].rowId").value(100));

        verify(todoProjectService).getProjects(1L);
    }

    @Test
    @DisplayName("PUT /todo-project/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateProject() throws Exception {
        given(todoProjectService.updateProject(eq(100L), eq(1L), any())).willReturn(sampleProject());

        String body = """
                {"projectName":"수정","description":"수정 설명","color":"#000000","icon":"star"}
                """;

        mockMvc.perform(put("/api/v1/todo-project/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TodoProjectServiceDto.UpdateCommand.class);
        verify(todoProjectService).updateProject(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().projectName()).isEqualTo("수정");
        assertThat(captor.getValue().color()).isEqualTo("#000000");
        assertThat(captor.getValue().icon()).isEqualTo("star");
    }

    @Test
    @DisplayName("PATCH /todo-projects/reorder — 정렬 항목 순서대로 위임")
    void reorderProjects() throws Exception {
        String body = """
                {"items":[{"projectId":1,"sortOrder":0},{"projectId":2,"sortOrder":1}]}
                """;

        mockMvc.perform(patch("/api/v1/todo-projects/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TodoProjectServiceDto.ReorderCommand.class);
        verify(todoProjectService).reorderProjects(eq(1L), captor.capture());
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().items().get(0).projectId()).isEqualTo(1L);
        assertThat(captor.getValue().items().get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /todo-project/{id} — id·로그인 사용자로 삭제 위임")
    void deleteProject() throws Exception {
        mockMvc.perform(delete("/api/v1/todo-project/{id}", 100L))
                .andExpect(status().isOk());

        verify(todoProjectService).deleteProject(100L, 1L);
    }
}
