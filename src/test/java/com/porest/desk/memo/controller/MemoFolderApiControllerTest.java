package com.porest.desk.memo.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.memo.service.MemoFolderService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MemoFolder API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = MemoFolderApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class MemoFolderApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MemoFolderService memoFolderService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private MemoServiceDto.FolderInfo sampleInfo() {
        return new MemoServiceDto.FolderInfo(
                100L, 1L, 5L, "폴더", 0,
                LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    @Test
    @DisplayName("POST /memo/folder — 로그인 사용자·바디로 createFolder 위임")
    void createFolder() throws Exception {
        given(memoFolderService.createFolder(any())).willReturn(sampleInfo());

        String body = """
                {"parentId":5,"folderName":"폴더"}
                """;

        mockMvc.perform(post("/api/v1/memo/folder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.folderName").value("폴더"));

        var captor = ArgumentCaptor.forClass(MemoServiceDto.FolderCreateCommand.class);
        verify(memoFolderService).createFolder(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().parentId()).isEqualTo(5L);
        assertThat(captor.getValue().folderName()).isEqualTo("폴더");
    }

    @Test
    @DisplayName("GET /memo/folders — 로그인 사용자로 목록 조회")
    void getFolders() throws Exception {
        given(memoFolderService.getFolders(1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/memo/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folders.length()").value(1))
                .andExpect(jsonPath("$.data.folders[0].rowId").value(100));

        verify(memoFolderService).getFolders(1L);
    }

    @Test
    @DisplayName("PUT /memo/folder/{id} — id·로그인 사용자·바디로 updateFolder 위임")
    void updateFolder() throws Exception {
        given(memoFolderService.updateFolder(eq(9L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"parentId":7,"folderName":"수정폴더","sortOrder":3}
                """;

        mockMvc.perform(put("/api/v1/memo/folder/{id}", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100));

        var captor = ArgumentCaptor.forClass(MemoServiceDto.FolderUpdateCommand.class);
        verify(memoFolderService).updateFolder(eq(9L), eq(1L), captor.capture());
        assertThat(captor.getValue().parentId()).isEqualTo(7L);
        assertThat(captor.getValue().folderName()).isEqualTo("수정폴더");
        assertThat(captor.getValue().sortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("DELETE /memo/folder/{id} — id·로그인 사용자로 삭제 위임")
    void deleteFolder() throws Exception {
        mockMvc.perform(delete("/api/v1/memo/folder/{id}", 9L))
                .andExpect(status().isOk());

        verify(memoFolderService).deleteFolder(9L, 1L);
    }
}
