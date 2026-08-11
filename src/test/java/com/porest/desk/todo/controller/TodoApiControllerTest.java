package com.porest.desk.todo.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.todo.service.TodoService;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
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
 * Todo API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 매핑·바디 역직렬화·쿼리 파라미터 변환·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = TodoApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class TodoApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TodoService todoService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private TodoServiceDto.TodoInfo sampleTodo() {
        return new TodoServiceDto.TodoInfo(
                100L, 1L, TodoType.TASK, "할일 제목", "내용",
                TodoPriority.HIGH, "work", TodoStatus.PENDING,
                LocalDate.of(2026, 7, 3), null, 0, YNType.N,
                null, List.of(), 0, 0, null, null, 0);
    }

    @Test
    @DisplayName("POST /todo — 로그인 사용자·바디로 createTodo 위임")
    void createTodo() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        String body = """
                {"title":"할일 제목","content":"내용","priority":"HIGH","category":"work",
                 "dueDate":"2026-07-03","parentRowId":null,
                 "tagIds":[1,2],"type":"TASK"}
                """;

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.title").value("할일 제목"));

        var captor = ArgumentCaptor.forClass(TodoServiceDto.CreateCommand.class);
        verify(todoService).createTodo(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().title()).isEqualTo("할일 제목");
        assertThat(captor.getValue().priority()).isEqualTo(TodoPriority.HIGH);
        assertThat(captor.getValue().type()).isEqualTo(TodoType.TASK);
        assertThat(captor.getValue().tagIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("GET /todos — 쿼리 파라미터가 enum·타입으로 변환되어 서비스에 전달")
    void getTodos_withFilters() throws Exception {
        given(todoService.getTodos(eq(1L), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(sampleTodo()));

        mockMvc.perform(get("/api/v1/todos")
                        .param("status", "PENDING")
                        .param("priority", "HIGH")
                        .param("category", "work")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31")
                        .param("type", "TASK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todos[0].rowId").value(100));

        verify(todoService).getTodos(1L, TodoStatus.PENDING, TodoPriority.HIGH, "work",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TodoType.TASK);
    }

    @Test
    @DisplayName("GET /todos — 파라미터 없으면 null 로 전달")
    void getTodos_noFilters() throws Exception {
        given(todoService.getTodos(eq(1L), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk());

        verify(todoService).getTodos(1L, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("GET /todo/{id} — path·로그인 사용자로 단건 조회")
    void getTodo() throws Exception {
        given(todoService.getTodo(100L, 1L)).willReturn(sampleTodo());

        mockMvc.perform(get("/api/v1/todo/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(todoService).getTodo(100L, 1L);
    }

    @Test
    @DisplayName("PUT /todo/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateTodo() throws Exception {
        given(todoService.updateTodo(eq(100L), eq(1L), any())).willReturn(sampleTodo());

        String body = """
                {"title":"수정 제목","content":"수정 내용","priority":"LOW","category":"life",
                 "dueDate":"2026-08-01","tagIds":[3]}
                """;

        mockMvc.perform(put("/api/v1/todo/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TodoServiceDto.UpdateCommand.class);
        verify(todoService).updateTodo(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("수정 제목");
        assertThat(captor.getValue().priority()).isEqualTo(TodoPriority.LOW);
        assertThat(captor.getValue().tagIds()).containsExactly(3L);
    }

    @Test
    @DisplayName("PATCH /todo/{id}/status — 상태 토글 위임")
    void toggleStatus() throws Exception {
        given(todoService.toggleStatus(100L, 1L)).willReturn(sampleTodo());

        mockMvc.perform(patch("/api/v1/todo/{id}/status", 100L))
                .andExpect(status().isOk());

        verify(todoService).toggleStatus(100L, 1L);
    }

    @Test
    @DisplayName("PATCH /todo/{id}/pin — 고정 토글 위임")
    void togglePin() throws Exception {
        given(todoService.togglePin(100L, 1L)).willReturn(sampleTodo());

        mockMvc.perform(patch("/api/v1/todo/{id}/pin", 100L))
                .andExpect(status().isOk());

        verify(todoService).togglePin(100L, 1L);
    }

    @Test
    @DisplayName("PATCH /todos/reorder — 정렬 항목 순서대로 위임")
    void reorderTodos() throws Exception {
        String body = """
                {"items":[{"todoId":1,"sortOrder":0},{"todoId":2,"sortOrder":1}]}
                """;

        mockMvc.perform(patch("/api/v1/todos/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(TodoServiceDto.ReorderCommand.class);
        verify(todoService).reorderTodos(eq(1L), captor.capture());
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().items().get(0).todoId()).isEqualTo(1L);
        assertThat(captor.getValue().items().get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /todo/{id} — id·로그인 사용자로 삭제 위임")
    void deleteTodo() throws Exception {
        mockMvc.perform(delete("/api/v1/todo/{id}", 100L))
                .andExpect(status().isOk());

        verify(todoService).deleteTodo(100L, 1L);
    }

    @Test
    @DisplayName("GET /todo/{id}/subtasks — 하위 작업 목록 조회")
    void getSubtasks() throws Exception {
        given(todoService.getSubtasks(100L, 1L)).willReturn(List.of(sampleTodo()));

        mockMvc.perform(get("/api/v1/todo/{id}/subtasks", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todos[0].rowId").value(100));

        verify(todoService).getSubtasks(100L, 1L);
    }

    @Test
    @DisplayName("PATCH /todo/{id}/tags — 태그 목록 갱신 위임")
    void updateTags() throws Exception {
        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[10,20,30]}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoService).updateTags(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue()).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("GET /todos/stats — 통계 조회 + 응답 매핑")
    void getStats() throws Exception {
        given(todoService.getStats(1L))
                .willReturn(new TodoServiceDto.TodoStats(10L, 3L, 2L, 5L, 1L, 0L, 4L));

        mockMvc.perform(get("/api/v1/todos/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.completedCount").value(5))
                .andExpect(jsonPath("$.data.noteCount").value(4));

        verify(todoService).getStats(1L);
    }
}
