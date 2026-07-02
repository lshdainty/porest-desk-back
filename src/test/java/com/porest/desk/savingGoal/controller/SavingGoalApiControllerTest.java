package com.porest.desk.savingGoal.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.savingGoal.service.SavingGoalService;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;
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
 * SavingGoal API 슬라이스 테스트 — 매핑·바디 역직렬화·로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = SavingGoalApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class SavingGoalApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SavingGoalService savingGoalService;
    @MockitoBean private MessageResolver messageResolver;

    private SavingGoalServiceDto.GoalInfo sampleGoal() {
        return new SavingGoalServiceDto.GoalInfo(
                100L, 1L, "여행 자금", "일본 여행", 1000000L, 250000L, "KRW",
                LocalDate.of(2026, 12, 31), "plane", "#00aaff", 7L, 0,
                YNType.N, null, null, null);
    }

    @Test
    @DisplayName("POST /saving-goal — 로그인 사용자·바디로 생성 위임")
    void createSavingGoal() throws Exception {
        given(savingGoalService.createSavingGoal(any())).willReturn(sampleGoal());

        String body = """
                {"title":"여행 자금","description":"일본 여행","targetAmount":1000000,"currency":"KRW",
                 "deadlineDate":"2026-12-31","icon":"plane","color":"#00aaff",
                 "linkedAssetRowId":7,"sortOrder":0}
                """;

        mockMvc.perform(post("/api/v1/saving-goal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.title").value("여행 자금"));

        var captor = ArgumentCaptor.forClass(SavingGoalServiceDto.CreateCommand.class);
        verify(savingGoalService).createSavingGoal(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().title()).isEqualTo("여행 자금");
        assertThat(captor.getValue().targetAmount()).isEqualTo(1000000L);
        assertThat(captor.getValue().currency()).isEqualTo("KRW");
        assertThat(captor.getValue().linkedAssetRowId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("GET /saving-goals — 로그인 사용자로 목록 조회")
    void getSavingGoals() throws Exception {
        given(savingGoalService.getSavingGoals(1L)).willReturn(List.of(sampleGoal()));

        mockMvc.perform(get("/api/v1/saving-goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].rowId").value(100));

        verify(savingGoalService).getSavingGoals(1L);
    }

    @Test
    @DisplayName("GET /saving-goal/{id} — path·로그인 사용자로 단건 조회")
    void getSavingGoal() throws Exception {
        given(savingGoalService.getSavingGoal(100L, 1L)).willReturn(sampleGoal());

        mockMvc.perform(get("/api/v1/saving-goal/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(250000));

        verify(savingGoalService).getSavingGoal(100L, 1L);
    }

    @Test
    @DisplayName("PUT /saving-goal/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateSavingGoal() throws Exception {
        given(savingGoalService.updateSavingGoal(eq(100L), eq(1L), any())).willReturn(sampleGoal());

        String body = """
                {"title":"수정 목표","description":"수정","targetAmount":2000000,
                 "deadlineDate":"2027-01-01","icon":"star","color":"#ff0000","linkedAssetRowId":9}
                """;

        mockMvc.perform(put("/api/v1/saving-goal/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(SavingGoalServiceDto.UpdateCommand.class);
        verify(savingGoalService).updateSavingGoal(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("수정 목표");
        assertThat(captor.getValue().targetAmount()).isEqualTo(2000000L);
        assertThat(captor.getValue().linkedAssetRowId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("PATCH /saving-goal/{id}/contribute — path·로그인 사용자·바디로 납입 위임")
    void contribute() throws Exception {
        given(savingGoalService.contribute(eq(100L), eq(1L), any())).willReturn(sampleGoal());

        mockMvc.perform(patch("/api/v1/saving-goal/{id}/contribute", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50000,\"note\":\"월 적립\"}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(SavingGoalServiceDto.ContributeCommand.class);
        verify(savingGoalService).contribute(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(50000L);
        assertThat(captor.getValue().note()).isEqualTo("월 적립");
    }

    @Test
    @DisplayName("DELETE /saving-goal/{id} — id·로그인 사용자로 삭제 위임")
    void deleteSavingGoal() throws Exception {
        mockMvc.perform(delete("/api/v1/saving-goal/{id}", 100L))
                .andExpect(status().isOk());

        verify(savingGoalService).deleteSavingGoal(100L, 1L);
    }

    @Test
    @DisplayName("PATCH /saving-goals/reorder — 정렬 항목 순서대로 위임")
    void reorderSavingGoals() throws Exception {
        String body = """
                {"items":[{"id":1,"sortOrder":0},{"id":2,"sortOrder":1}]}
                """;

        mockMvc.perform(patch("/api/v1/saving-goals/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SavingGoalServiceDto.ReorderItem>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(savingGoalService).reorderSavingGoals(eq(1L), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).id()).isEqualTo(1L);
        assertThat(captor.getValue().get(1).sortOrder()).isEqualTo(1);
    }
}
