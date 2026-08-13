package com.porest.desk.dataimport.sms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.dataimport.sms.controller.dto.SmsImportApiDto;
import com.porest.desk.dataimport.sms.service.SmsConfidence;
import com.porest.desk.dataimport.sms.service.SmsImportService;
import com.porest.desk.dataimport.sms.service.dto.SmsImportServiceDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 문자 API 슬라이스 테스트. 보안 필터는 끄고 {@link WithLoginUser} 로 로그인 사용자 주입.
 */
@WebMvcTest(controllers = SmsImportApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class SmsImportApiControllerTest {

    @Autowired private MockMvc mockMvc;
    /** 슬라이스에 Jackson 빈이 안 올라오므로 직접 만든다 — 요청 본문 직렬화에만 쓴다. */
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean private SmsImportService smsImportService;
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST /import/sms/parse — 원문을 넘기고 초안을 받는다")
    void parse() throws Exception {
        given(smsImportService.parse(any(), eq(1L))).willReturn(
            new SmsImportServiceDto.ParseResult(
                true, SmsConfidence.HIGH, false, 5_500L, "스타벅스강남",
                LocalDateTime.of(2026, 8, 13, 13, 22), null,
                "KB국민카드|1234", "KB국민카드", "1234",
                100L, true, List.of(), 50L, "카페", null, null));

        mockMvc.perform(post("/api/v1/import/sms/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"KB국민카드1234승인 5,500원 일시불 08/13 13:22 스타벅스강남\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.matched").value(true))
            .andExpect(jsonPath("$.data.amount").value(5500))
            .andExpect(jsonPath("$.data.merchant").value("스타벅스강남"))
            .andExpect(jsonPath("$.data.assetRowId").value(100))
            .andExpect(jsonPath("$.data.categoryName").value("카페"))
            // 오프셋 없는 로컬 시각으로 내려간다 — UTC 로 바뀌면 자정 근처 날짜가 밀린다.
            .andExpect(jsonPath("$.data.expenseDate").value("2026-08-13T13:22"));
    }

    @Test
    @DisplayName("POST /import/sms/parse — 결제 문자가 아니면 matched=false")
    void parseNoMatch() throws Exception {
        given(smsImportService.parse(any(), eq(1L)))
            .willReturn(SmsImportServiceDto.ParseResult.noMatch());

        mockMvc.perform(post("/api/v1/import/sms/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"오늘 저녁에 만나자\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.matched").value(false));
    }

    @Test
    @DisplayName("POST /import/sms/commit — 확정 값과 로그인 사용자를 서비스로 넘긴다")
    void commit() throws Exception {
        given(smsImportService.commit(any()))
            .willReturn(new SmsImportServiceDto.CommitResult(500L, true));

        SmsImportApiDto.CommitRequest request = new SmsImportApiDto.CommitRequest(
            "KB국민카드1234승인 5,500원 일시불 08/13 13:22 스타벅스강남",
            100L, 50L, 5_500L, "스타벅스강남", null,
            "2026-08-13T13:22", null, null, null, null, true);

        mockMvc.perform(post("/api/v1/import/sms/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expenseRowId").value(500))
            .andExpect(jsonPath("$.data.cardRemembered").value(true));

        ArgumentCaptor<SmsImportServiceDto.CommitCommand> captor =
            ArgumentCaptor.forClass(SmsImportServiceDto.CommitCommand.class);
        verify(smsImportService).commit(captor.capture());
        SmsImportServiceDto.CommitCommand cmd = captor.getValue();
        assertThat(cmd.userRowId()).isEqualTo(1L);
        assertThat(cmd.expenseDate()).isEqualTo(LocalDateTime.of(2026, 8, 13, 13, 22));
        assertThat(cmd.rememberCard()).isTrue();
    }

    @Test
    @DisplayName("POST /import/sms/commit — 날짜만 온 경우 그 날 00:00 으로 읽는다")
    void commitDateOnly() throws Exception {
        given(smsImportService.commit(any()))
            .willReturn(new SmsImportServiceDto.CommitResult(500L, false));

        SmsImportApiDto.CommitRequest request = new SmsImportApiDto.CommitRequest(
            "KB국민카드1234승인 5,500원 일시불 스타벅스강남",
            100L, 50L, 5_500L, "스타벅스강남", null,
            "2026-08-13", null, null, null, null, false);

        mockMvc.perform(post("/api/v1/import/sms/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        ArgumentCaptor<SmsImportServiceDto.CommitCommand> captor =
            ArgumentCaptor.forClass(SmsImportServiceDto.CommitCommand.class);
        verify(smsImportService).commit(captor.capture());
        assertThat(captor.getValue().expenseDate()).isEqualTo(LocalDateTime.of(2026, 8, 13, 0, 0));
    }

    @Test
    @DisplayName("GET /import/sms/cards — 기억해 둔 매핑 목록")
    void cardMappings() throws Exception {
        given(smsImportService.getCardMappings(1L)).willReturn(List.of(
            new SmsImportServiceDto.CardMappingInfo(1L, "KB국민카드|1234", 100L, "KB 국민 체크")));

        mockMvc.perform(get("/api/v1/import/sms/cards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mappings[0].cardHint").value("KB국민카드|1234"))
            .andExpect(jsonPath("$.data.mappings[0].assetName").value("KB 국민 체크"));
    }

    @Test
    @DisplayName("DELETE /import/sms/cards/{id} — 로그인 사용자로 위임한다")
    void deleteCardMapping() throws Exception {
        mockMvc.perform(delete("/api/v1/import/sms/cards/9"))
            .andExpect(status().isOk());

        verify(smsImportService).deleteCardMapping(9L, 1L);
    }
}
