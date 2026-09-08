package com.porest.desk.memo.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import com.porest.desk.memo.service.MemoTagService;
import com.porest.desk.memo.service.dto.MemoTagServiceDto;
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
 * MemoTag API 슬라이스 테스트 — 매핑·바디 역직렬화·로그인 사용자 위임 검증.
 *
 * <p>경로·본문·응답이 {@code /todo-tag} 와 같은 모양이라 그 테스트의 미러다. 설정 화면이
 * 두 태그를 나란히 놓으므로 API 가 갈라지면 화면이 두 벌로 갈린다.
 */
@WebMvcTest(controllers = MemoTagApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class MemoTagApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MemoTagService memoTagService;
    @MockitoBean private MessageResolver messageResolver;

    private MemoTagServiceDto.TagInfo sampleTag() {
        return new MemoTagServiceDto.TagInfo(100L, 1L, "긴급", "#ff0000", null, null, 0L);
    }

    @Test
    @DisplayName("POST /memo-tag — 로그인 사용자·바디로 생성 위임")
    void createTag() throws Exception {
        given(memoTagService.createTag(any())).willReturn(sampleTag());

        mockMvc.perform(post("/api/v1/memo-tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"긴급\",\"color\":\"#ff0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.tagName").value("긴급"));

        var captor = ArgumentCaptor.forClass(MemoTagServiceDto.CreateCommand.class);
        verify(memoTagService).createTag(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().tagName()).isEqualTo("긴급");
        assertThat(captor.getValue().color()).isEqualTo("#ff0000");
    }

    @Test
    @DisplayName("GET /memo-tags — 로그인 사용자로 목록 조회")
    void getTags() throws Exception {
        given(memoTagService.getTags(1L)).willReturn(List.of(sampleTag()));

        mockMvc.perform(get("/api/v1/memo-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].rowId").value(100));

        verify(memoTagService).getTags(1L);
    }

    @Test
    @DisplayName("PUT /memo-tag/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateTag() throws Exception {
        given(memoTagService.updateTag(eq(100L), eq(1L), any())).willReturn(sampleTag());

        mockMvc.perform(put("/api/v1/memo-tag/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"보통\",\"color\":\"#00ff00\"}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(MemoTagServiceDto.UpdateCommand.class);
        verify(memoTagService).updateTag(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().tagName()).isEqualTo("보통");
        assertThat(captor.getValue().color()).isEqualTo("#00ff00");
    }

    @Test
    @DisplayName("DELETE /memo-tag/{id} — id·로그인 사용자로 삭제 위임")
    void deleteTag() throws Exception {
        mockMvc.perform(delete("/api/v1/memo-tag/{id}", 100L))
                .andExpect(status().isOk());

        verify(memoTagService).deleteTag(100L, 1L);
    }

    @Test
    @DisplayName("GET /memo-tags — 응답에 usageCount 가 실린다(삭제 확인창이 읽는 숫자)")
    void listCarriesUsageCount() throws Exception {
        given(memoTagService.getTags(1L)).willReturn(List.of(
                new MemoTagServiceDto.TagInfo(100L, 1L, "업무", "#ff0000", null, null, 3L)));

        mockMvc.perform(get("/api/v1/memo-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].usageCount").value(3));
    }
}
