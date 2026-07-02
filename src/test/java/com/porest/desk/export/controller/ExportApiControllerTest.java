package com.porest.desk.export.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.export.controller.dto.ExportApiDto;
import com.porest.desk.export.service.ExportService;
import com.porest.desk.export.type.ExportFormat;
import com.porest.desk.export.type.ExportPeriod;
import com.porest.desk.export.type.ExportType;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 *
 * <p>{@code /export} 는 {@code StreamingResponseBody} → 비동기 처리이므로
 * {@code request().asyncStarted()} 후 {@code asyncDispatch} 로 최종 응답(헤더·바이트)을 검증한다.
 */
@WebMvcTest(controllers = ExportApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ExportApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExportService exportService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    @Test
    @DisplayName("POST /export — describe 헤더 반영 + 스트리밍 바디를 로그인 사용자로 기록")
    void export_streamsFileWithHeaders() throws Exception {
        given(exportService.describe(any()))
                .willReturn(new ExportService.ExportDescriptor("export.csv", "text/csv; charset=UTF-8"));
        willAnswer(inv -> {
            OutputStream out = inv.getArgument(0);
            out.write("id,name\n1,foo\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).given(exportService).writeExport(any(), any(), eq(1L));

        String body = """
                {"format":"CSV","period":"THIS_MONTH","startDate":null,"endDate":null,
                 "types":["EXPENSE","TODO"],"mask":false}
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export.csv\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(content().string("id,name\n1,foo\n"));

        // 요청 바디의 종 선택이 서비스로 그대로 전달되는지 확인.
        ArgumentCaptor<ExportApiDto.ExportRequest> captor =
                ArgumentCaptor.forClass(ExportApiDto.ExportRequest.class);
        verify(exportService).writeExport(any(), captor.capture(), eq(1L));
        assertThat(captor.getValue().format()).isEqualTo(ExportFormat.CSV);
        assertThat(captor.getValue().period()).isEqualTo(ExportPeriod.THIS_MONTH);
        assertThat(captor.getValue().types()).containsExactly(ExportType.EXPENSE, ExportType.TODO);
    }

    @Test
    @DisplayName("POST /export/counts — 로그인 사용자·바디로 건수 조회 위임")
    void counts() throws Exception {
        given(exportService.counts(any(), eq(1L)))
                .willReturn(new ExportApiDto.CountResponse(List.of(
                        new ExportApiDto.TypeCount("expense", "거래 내역", 12))));

        String body = """
                {"period":"THIS_MONTH","startDate":null,"endDate":null,"types":["EXPENSE"]}
                """;

        mockMvc.perform(post("/api/v1/export/counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts[0].type").value("expense"))
                .andExpect(jsonPath("$.data.counts[0].count").value(12));

        verify(exportService).counts(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /export/preview — 로그인 사용자·바디로 미리보기 위임")
    void preview() throws Exception {
        given(exportService.preview(any(), eq(1L)))
                .willReturn(new ExportApiDto.PreviewResponse(List.of(
                        new ExportApiDto.PreviewTable(
                                "expense", "거래 내역",
                                List.of("날짜", "금액"),
                                List.of(List.of("2026-07-01", "1000")),
                                5))));

        String body = """
                {"period":"THIS_MONTH","startDate":null,"endDate":null,"types":["EXPENSE"]}
                """;

        mockMvc.perform(post("/api/v1/export/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tables[0].type").value("expense"))
                .andExpect(jsonPath("$.data.tables[0].totalCount").value(5))
                .andExpect(jsonPath("$.data.tables[0].headers[0]").value("날짜"));

        verify(exportService).preview(any(), eq(1L));
    }
}
