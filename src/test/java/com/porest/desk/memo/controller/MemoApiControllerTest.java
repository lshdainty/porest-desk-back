package com.porest.desk.memo.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.memo.service.MemoService;
import com.porest.desk.memo.service.dto.MemoServiceDto;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Memo API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 매핑·바디 역직렬화·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = MemoApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class MemoApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MemoService memoService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private MemoServiceDto.MemoInfo sampleInfo() {
        return new MemoServiceDto.MemoInfo(
                100L, 1L, 10L, "제목", "내용", "태그", "#fff",
                YNType.N, LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    @Test
    @DisplayName("POST /memo — 로그인 사용자·바디로 createMemo 위임")
    void createMemo() throws Exception {
        given(memoService.createMemo(any())).willReturn(sampleInfo());

        String body = """
                {"folderId":10,"title":"제목","content":"내용","tag":"태그","color":"#fff"}
                """;

        mockMvc.perform(post("/api/v1/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.title").value("제목"));

        var captor = ArgumentCaptor.forClass(MemoServiceDto.CreateCommand.class);
        verify(memoService).createMemo(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().folderId()).isEqualTo(10L);
        assertThat(captor.getValue().title()).isEqualTo("제목");
        assertThat(captor.getValue().content()).isEqualTo("내용");
        assertThat(captor.getValue().tag()).isEqualTo("태그");
        assertThat(captor.getValue().color()).isEqualTo("#fff");
    }

    @Test
    @DisplayName("GET /memos — 로그인 사용자·folderId·search 쿼리로 목록 조회")
    void getMemos() throws Exception {
        given(memoService.getMemos(1L, 10L, "keyword")).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/memos")
                        .param("folderId", "10")
                        .param("search", "keyword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memos.length()").value(1))
                .andExpect(jsonPath("$.data.memos[0].rowId").value(100));

        verify(memoService).getMemos(1L, 10L, "keyword");
    }

    @Test
    @DisplayName("GET /memos — 쿼리 없으면 folderId·search null 로 위임")
    void getMemosWithoutParams() throws Exception {
        given(memoService.getMemos(1L, null, null)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/memos"))
                .andExpect(status().isOk());

        verify(memoService).getMemos(1L, null, null);
    }

    @Test
    @DisplayName("GET /memo/{id} — id·로그인 사용자로 단건 조회")
    void getMemo() throws Exception {
        given(memoService.getMemo(5L, 1L)).willReturn(sampleInfo());

        mockMvc.perform(get("/api/v1/memo/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100));

        verify(memoService).getMemo(5L, 1L);
    }

    @Test
    @DisplayName("PUT /memo/{id} — id·로그인 사용자·바디로 updateMemo 위임")
    void updateMemo() throws Exception {
        given(memoService.updateMemo(eq(5L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"folderId":20,"title":"수정제목","content":"수정내용","tag":"수정태그","color":"#000"}
                """;

        mockMvc.perform(put("/api/v1/memo/{id}", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100));

        var captor = ArgumentCaptor.forClass(MemoServiceDto.UpdateCommand.class);
        verify(memoService).updateMemo(eq(5L), eq(1L), captor.capture());
        assertThat(captor.getValue().folderId()).isEqualTo(20L);
        assertThat(captor.getValue().title()).isEqualTo("수정제목");
        assertThat(captor.getValue().content()).isEqualTo("수정내용");
        assertThat(captor.getValue().tag()).isEqualTo("수정태그");
        assertThat(captor.getValue().color()).isEqualTo("#000");
    }

    @Test
    @DisplayName("PATCH /memo/{id}/pin — id·로그인 사용자로 togglePin 위임")
    void togglePin() throws Exception {
        given(memoService.togglePin(5L, 1L)).willReturn(sampleInfo());

        mockMvc.perform(patch("/api/v1/memo/{id}/pin", 5L))
                .andExpect(status().isOk());

        verify(memoService).togglePin(5L, 1L);
    }

    @Test
    @DisplayName("DELETE /memo/{id} — id·로그인 사용자로 삭제 위임")
    void deleteMemo() throws Exception {
        mockMvc.perform(delete("/api/v1/memo/{id}", 5L))
                .andExpect(status().isOk());

        verify(memoService).deleteMemo(5L, 1L);
    }
}
