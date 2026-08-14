package com.porest.desk.dutchpay.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.dutchpay.service.DutchPayService;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;
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
 * DutchPay API 슬라이스 테스트 — 매핑·바디 역직렬화(참가자 목록)·로그인 사용자 위임 검증.
 */
@WebMvcTest(controllers = DutchPayApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class DutchPayApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DutchPayService dutchPayService;
    @MockitoBean private MessageResolver messageResolver;

    private DutchPayServiceDto.DutchPayInfo sampleDutchPay() {
        DutchPayServiceDto.ParticipantInfo participant =
                new DutchPayServiceDto.ParticipantInfo(200L, 2L, "홍길동", 10000L, true, false, null);
        return new DutchPayServiceDto.DutchPayInfo(
                100L, 1L, 50L, "저녁 정산", "회식", 30000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 7, 3), false,
                List.of(participant), null, null);
    }

    @Test
    @DisplayName("POST /dutch-pay — 로그인 사용자·바디(참가자 포함)로 생성 위임")
    void createDutchPay() throws Exception {
        given(dutchPayService.createDutchPay(any())).willReturn(sampleDutchPay());

        String body = """
                {"sourceExpenseRowId":50,"title":"저녁 정산","description":"회식",
                 "totalAmount":30000,"currency":"KRW","splitMethod":"EQUAL","dutchPayDate":"2026-07-03",
                 "participants":[{"userRowId":2,"participantName":"홍길동","amount":10000},
                                 {"userRowId":null,"participantName":"익명","amount":20000}]}
                """;

        mockMvc.perform(post("/api/v1/dutch-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.participants[0].participantName").value("홍길동"))
                // 키 이름을 못 박는다 — Jackson 이 record 의 isPayer 를 payer 로 줄이면
                // 웹·앱이 결제자를 못 읽고 조용히 전원 참여자로 그린다
                .andExpect(jsonPath("$.data.participants[0].isPayer").value(true))
                .andExpect(jsonPath("$.data.participants[0].isPaid").value(false));

        var captor = ArgumentCaptor.forClass(DutchPayServiceDto.CreateCommand.class);
        verify(dutchPayService).createDutchPay(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().sourceExpenseRowId()).isEqualTo(50L);
        assertThat(captor.getValue().title()).isEqualTo("저녁 정산");
        assertThat(captor.getValue().splitMethod()).isEqualTo(SplitMethod.EQUAL);
        assertThat(captor.getValue().participants()).hasSize(2);
        assertThat(captor.getValue().participants().get(0).participantName()).isEqualTo("홍길동");
        assertThat(captor.getValue().participants().get(1).amount()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("POST /dutch-pay — participants 누락 시 빈 목록으로 위임")
    void createDutchPay_nullParticipants() throws Exception {
        given(dutchPayService.createDutchPay(any())).willReturn(sampleDutchPay());

        String body = """
                {"title":"정산","totalAmount":1000,"currency":"KRW","splitMethod":"EQUAL"}
                """;

        mockMvc.perform(post("/api/v1/dutch-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(DutchPayServiceDto.CreateCommand.class);
        verify(dutchPayService).createDutchPay(captor.capture());
        assertThat(captor.getValue().participants()).isEmpty();
    }

    @Test
    @DisplayName("GET /dutch-pays — 로그인 사용자로 목록 조회")
    void getDutchPays() throws Exception {
        given(dutchPayService.getDutchPays(1L)).willReturn(List.of(sampleDutchPay()));

        mockMvc.perform(get("/api/v1/dutch-pays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dutchPays[0].rowId").value(100));

        verify(dutchPayService).getDutchPays(1L);
    }

    @Test
    @DisplayName("GET /dutch-pay/{id} — path·로그인 사용자로 단건 조회")
    void getDutchPay() throws Exception {
        given(dutchPayService.getDutchPay(100L, 1L)).willReturn(sampleDutchPay());

        mockMvc.perform(get("/api/v1/dutch-pay/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(100))
                .andExpect(jsonPath("$.data.splitMethod").value("EQUAL"));

        verify(dutchPayService).getDutchPay(100L, 1L);
    }

    @Test
    @DisplayName("PUT /dutch-pay/{id} — path·로그인 사용자·바디로 수정 위임")
    void updateDutchPay() throws Exception {
        given(dutchPayService.updateDutchPay(eq(100L), eq(1L), any())).willReturn(sampleDutchPay());

        String body = """
                {"title":"수정 정산","description":"수정","totalAmount":40000,"currency":"USD",
                 "splitMethod":"CUSTOM","dutchPayDate":"2026-08-01",
                 "participants":[{"userRowId":3,"participantName":"김철수","amount":40000}]}
                """;

        mockMvc.perform(put("/api/v1/dutch-pay/{id}", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(DutchPayServiceDto.UpdateCommand.class);
        verify(dutchPayService).updateDutchPay(eq(100L), eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("수정 정산");
        assertThat(captor.getValue().currency()).isEqualTo("USD");
        assertThat(captor.getValue().splitMethod()).isEqualTo(SplitMethod.CUSTOM);
        assertThat(captor.getValue().participants()).hasSize(1);
        assertThat(captor.getValue().participants().get(0).participantName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("DELETE /dutch-pay/{id} — id·로그인 사용자로 삭제 위임")
    void deleteDutchPay() throws Exception {
        mockMvc.perform(delete("/api/v1/dutch-pay/{id}", 100L))
                .andExpect(status().isOk());

        verify(dutchPayService).deleteDutchPay(100L, 1L);
    }

    @Test
    @DisplayName("PATCH /dutch-pay/{id}/participant/{participantId}/paid — 참가자 정산 처리 위임")
    void markParticipantPaid() throws Exception {
        given(dutchPayService.markParticipantPaid(100L, 1L, 200L)).willReturn(sampleDutchPay());

        mockMvc.perform(patch("/api/v1/dutch-pay/{id}/participant/{participantId}/paid", 100L, 200L))
                .andExpect(status().isOk());

        verify(dutchPayService).markParticipantPaid(100L, 1L, 200L);
    }

    @Test
    @DisplayName("PATCH /dutch-pay/{id}/settle — 전체 정산 위임")
    void settleAll() throws Exception {
        given(dutchPayService.settleAll(100L, 1L)).willReturn(sampleDutchPay());

        mockMvc.perform(patch("/api/v1/dutch-pay/{id}/settle", 100L))
                .andExpect(status().isOk());

        verify(dutchPayService).settleAll(100L, 1L);
    }
}
