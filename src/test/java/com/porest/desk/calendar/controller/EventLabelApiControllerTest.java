package com.porest.desk.calendar.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.calendar.service.EventLabelService;
import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
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
 * EventLabel API 슬라이스 테스트 — 보안 필터 제외 + {@link WithLoginUser} 로 로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = EventLabelApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class EventLabelApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EventLabelService eventLabelService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private EventLabelServiceDto.LabelInfo sampleInfo() {
        return new EventLabelServiceDto.LabelInfo(30L, 1L, "중요", "#f00", 0);
    }

    @Test
    @DisplayName("POST /calendar/label — 로그인 사용자·바디로 createLabel 위임 + 응답 본문")
    void createLabel() throws Exception {
        given(eventLabelService.createLabel(any())).willReturn(sampleInfo());

        String body = """
                {"labelName":"중요","color":"#f00"}
                """;

        mockMvc.perform(post("/api/v1/calendar/label")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(30))
                .andExpect(jsonPath("$.data.labelName").value("중요"));

        var captor = ArgumentCaptor.forClass(EventLabelServiceDto.CreateCommand.class);
        verify(eventLabelService).createLabel(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().labelName()).isEqualTo("중요");
        assertThat(captor.getValue().color()).isEqualTo("#f00");
    }

    @Test
    @DisplayName("GET /calendar/labels — 로그인 사용자로 목록 조회")
    void getLabels() throws Exception {
        given(eventLabelService.getLabels(1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/calendar/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.length()").value(1))
                .andExpect(jsonPath("$.data.labels[0].labelName").value("중요"));

        verify(eventLabelService).getLabels(1L);
    }

    @Test
    @DisplayName("PUT /calendar/label/{id} — id·로그인 사용자·바디로 updateLabel 위임")
    void updateLabel() throws Exception {
        given(eventLabelService.updateLabel(eq(30L), eq(1L), any())).willReturn(sampleInfo());

        String body = """
                {"labelName":"보통","color":"#0f0"}
                """;

        mockMvc.perform(put("/api/v1/calendar/label/{id}", 30L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(30));

        var captor = ArgumentCaptor.forClass(EventLabelServiceDto.UpdateCommand.class);
        verify(eventLabelService).updateLabel(eq(30L), eq(1L), captor.capture());
        assertThat(captor.getValue().labelName()).isEqualTo("보통");
        assertThat(captor.getValue().color()).isEqualTo("#0f0");
    }

    @Test
    @DisplayName("DELETE /calendar/label/{id} — id·로그인 사용자로 삭제 위임")
    void deleteLabel() throws Exception {
        mockMvc.perform(delete("/api/v1/calendar/label/{id}", 30L))
                .andExpect(status().isOk());

        verify(eventLabelService).deleteLabel(eq(30L), eq(1L));
    }
}
