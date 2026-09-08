package com.porest.desk.todo.controller;

import com.porest.desk.common.patch.Patch;
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
import static org.mockito.Mockito.never;
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
                List.of(), null, null, 0);
    }

    @Test
    @DisplayName("POST /todo — 로그인 사용자·바디로 createTodo 위임")
    void createTodo() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        String body = """
                {"title":"할일 제목","content":"내용","priority":"HIGH","category":"work",
                 "dueDate":"2026-07-03",
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
        assertThat(captor.getValue().title()).isEqualTo(Patch.set("수정 제목"));
        assertThat(captor.getValue().priority()).isEqualTo(Patch.set(TodoPriority.LOW));
        assertThat(captor.getValue().tagIds()).containsExactly(3L);
    }

    /**
     * 웹({@code todoApi.toggleTodoStatus})이 본문 없이 부르는 자리 — <b>이게 깨지면 완료 체크가
     * 통째로 400 이 된다.</b> 본문 없음은 서비스에 {@code status = null}(= 종전 토글)로 간다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): 컨트롤러의 {@code @RequestBody(required = false)} 에서
     * {@code required = false} 를 빼면 이 요청이 400 이 되어 깨진다.
     */
    @Test
    @DisplayName("PATCH /todo/{id}/status — 본문 없는 옛 요청은 종전대로 토글(status=null 위임)")
    void changeStatusWithoutBodyTogglesAsBefore() throws Exception {
        given(todoService.changeStatus(100L, 1L, null)).willReturn(sampleTodo());

        mockMvc.perform(patch("/api/v1/todo/{id}/status", 100L))
                .andExpect(status().isOk());

        verify(todoService).changeStatus(100L, 1L, null);
    }

    /**
     * 앱({@code todo_repository.setStatus})이 보내는 모양 — 종전엔 서버가 본문을 안 읽어
     * "진행 중" 을 골라도 완료가 됐다(QA #93).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): 컨트롤러에서 {@code request.status()} 대신 {@code null} 을
     * 넘기면(= 종전 동작) 이 단언이 깨진다.
     */
    @Test
    @DisplayName("PATCH /todo/{id}/status — 본문 status 를 그대로 위임한다(IN_PROGRESS)")
    void changeStatusHonorsBody() throws Exception {
        given(todoService.changeStatus(100L, 1L, TodoStatus.IN_PROGRESS)).willReturn(sampleTodo());

        mockMvc.perform(patch("/api/v1/todo/{id}/status", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        verify(todoService).changeStatus(100L, 1L, TodoStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("PATCH /todo/{id}/status — 없는 상태 값은 400(역직렬화에서 끊긴다)")
    void changeStatusRejectsUnknownValue() throws Exception {
        mockMvc.perform(patch("/api/v1/todo/{id}/status", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).changeStatus(any(), any(), any());
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

    /**
     * 하위 할 일 개념을 걷었다(사용자 결정 2026-09-08) — 이 경로는 <b>더는 없다</b>.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TodoApiController} 에
     * {@code @GetMapping("/todo/{id}/subtasks")} 를 되살리면 아래가 404 대신 200 이 되며 깨진다.
     */
    @Test
    @DisplayName("GET /todo/{id}/subtasks — 없어진 경로다(404)")
    void subtasksEndpointIsGone() throws Exception {
        mockMvc.perform(get("/api/v1/todo/{id}/subtasks", 100L))
                .andExpect(status().isNotFound());
    }

    /**
     * 옛 클라이언트가 {@code parentRowId} 를 계속 실어 보내도 <b>400 이 아니라 무시</b>다.
     * 400 으로 끊으면 옛 앱의 하위 빠른 추가가 통째로 에러를 맞는데, 그 키는 사용자가 채운
     * 값이 아니라 화면이 붙인 값이라 사용자가 고칠 방법이 없다 — 적은 제목 그대로 보통 할 일로
     * 남는 쪽이 낫다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code application.yml} 에
     * {@code spring.jackson.deserialization.fail-on-unknown-properties: true} 를 켜면 400 이 되며 깨진다.
     */
    @Test
    @DisplayName("POST /todo — 옛 클라이언트의 parentRowId 는 400 이 아니라 무시된다")
    void createIgnoresLegacyParentRowId() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"하위 할 일","parentRowId":100,"type":"TASK"}
                                """))
                .andExpect(status().isOk());

        verify(todoService).createTodo(any());
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

    /**
     * QA #87 — 값을 빠뜨린 요청이 붙여 둔 태그를 조용히 다 지웠다. 세 모양을 한 자리에 묶어 둔다:
     * 키 없음 · null 은 400 이고, <b>빈 배열만</b> "전부 해제" 다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TagUpdateRequest.tagIds} 의 {@code @NotNull} 이나
     * 컨트롤러의 {@code @Valid} 중 하나만 빼도 앞의 두 케이스가 200 이 되면서 깨진다.
     */
    @Test
    @DisplayName("PATCH /todo/{id}/tags — 키 없음·null 은 400, 빈 배열만 전부 해제")
    void updateTagsRejectsMissingListButAcceptsEmptyOne() throws Exception {
        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":null}"))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).updateTags(any(), any(), any());

        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[]}"))
                .andExpect(status().isOk());

        verify(todoService).updateTags(100L, 1L, List.of());
    }

    /**
     * QA #100 — 위 세 모양을 막고도 <b>원소 null</b> 하나가 남아 있었다.
     * {@code {"tagIds":[null]}} 은 검증을 통과했고, {@code TodoServiceImpl.resolveOwnedTags} 가
     * null 원소를 {@code filter(Objects::nonNull)} 로 버려 <b>빈 목록</b>이 됐다 — 즉 위에서
     * "전부 해제" 로 확정한 그 뜻으로 흘러 붙여 둔 태그가 다 지워졌다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TagUpdateRequest} 의 원소 제약
     * {@code List<@NotNull Long>} 을 {@code List<Long>} 으로 되돌리면 이 테스트가 200 을 받아
     * 깨진다. 위 {@code @NotNull} 만으로는 못 막는다 — 목록 자체는 null 이 아니기 때문이다.
     */
    @Test
    @DisplayName("PATCH /todo/{id}/tags — 원소가 null 이면 400 (종전 200: 태그가 전부 해제됐다)")
    void updateTagsRejectsNullElement() throws Exception {
        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[null]}"))
                .andExpect(status().isBadRequest());

        // 섞여 들어온 경우도 같다 — 하나라도 null 이면 목록 전체를 못 믿는다.
        mockMvc.perform(patch("/api/v1/todo/{id}/tags", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[10,null,20]}"))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).updateTags(any(), any(), any());
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

    @Test
    @DisplayName("POST /todo — 존재하지 않는 마감일(2026-02-30)은 400 (종전엔 500)")
    void createTodoRejectsImpossibleDueDate() throws Exception {
        String body = """
                {"title":"할일","priority":"HIGH","dueDate":"2026-02-30","type":"TASK"}
                """;

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).createTodo(any());
    }

    @Test
    @DisplayName("PUT /todo/{id} — 존재하지 않는 마감일(2026-02-30)은 400")
    void updateTodoRejectsImpossibleDueDate() throws Exception {
        String body = """
                {"title":"할일","priority":"HIGH","dueDate":"2026-02-30"}
                """;

        mockMvc.perform(put("/api/v1/todo/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).updateTodo(any(Long.class), any(Long.class), any());
    }

    @Test
    @DisplayName("POST /todo — 1900·2099 마감일은 허용한다(범위 제한 없음 — QA #11 결정)")
    void createTodoAllowsFarDueDates() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        for (String dueDate : new String[]{"1900-01-01", "2099-12-31"}) {
            mockMvc.perform(post("/api/v1/todo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"할일\",\"priority\":\"HIGH\",\"dueDate\":\""
                                    + dueDate + "\",\"type\":\"TASK\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("POST /todo — 제목 201자는 400 (종전엔 DB 제약에 걸려 500)")
    void createTodoRejectsLongTitle() throws Exception {
        String body = """
                {"title":"%s","priority":"HIGH","type":"TASK"}
                """.formatted("가".repeat(201));

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).createTodo(any());
    }

    @Test
    @DisplayName("POST /todo — 제목 200자(경계)는 통과")
    void createTodoAcceptsTitleAtLimit() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        String body = """
                {"title":"%s","priority":"HIGH","type":"TASK"}
                """.formatted("가".repeat(200));

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(todoService).createTodo(any());
    }

    @Test
    @DisplayName("PUT /todo/{id} — 제목 201자는 400")
    void updateTodoRejectsLongTitle() throws Exception {
        String body = """
                {"title":"%s","priority":"HIGH"}
                """.formatted("가".repeat(201));

        mockMvc.perform(put("/api/v1/todo/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).updateTodo(any(Long.class), any(Long.class), any());
    }

    @Test
    @DisplayName("POST /todo — 메모 10,001자는 400 (공통 상한 10,000)")
    void createTodoRejectsOversizedContent() throws Exception {
        String body = "{\"title\":\"할일\",\"priority\":\"HIGH\",\"type\":\"TASK\",\"content\":\""
                + "가".repeat(10_001) + "\"}";

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(todoService, never()).createTodo(any());
    }

    @Test
    @DisplayName("POST /todo — 메모 10,000자(경계)는 통과")
    void createTodoAcceptsContentAtLimit() throws Exception {
        given(todoService.createTodo(any())).willReturn(sampleTodo());

        String body = "{\"title\":\"할일\",\"priority\":\"HIGH\",\"type\":\"TASK\",\"content\":\""
                + "가".repeat(10_000) + "\"}";

        mockMvc.perform(post("/api/v1/todo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(todoService).createTodo(any());
    }
}
