package com.porest.desk.stock.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.stock.service.StockWatchService;
import com.porest.desk.stock.service.dto.StockWatchServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관심목록 API 슬라이스 테스트 — 요청 매핑·응답 변환·로그인 사용자 전달을 검증한다. 서비스는 mock.
 */
@WebMvcTest(controllers = StockWatchApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class StockWatchApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StockWatchService stockWatchService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private StockWatchServiceDto.ItemInfo appleItem() {
        return new StockWatchServiceDto.ItemInfo(
                5L, 100L, "US", StockMarket.NAS, "AAPL", "애플", "APPLE INC",
                StockSecurityType.STOCK, "USD");
    }

    @Test
    @DisplayName("GET /stock-watch/groups — 그룹과 소속 종목(마스터 정보 포함)을 돌려준다")
    void getGroups() throws Exception {
        given(stockWatchService.getGroups(1L)).willReturn(List.of(
                new StockWatchServiceDto.GroupInfo(10L, "관심", 0, List.of(appleItem()))));

        mockMvc.perform(get("/api/v1/stock-watch/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].groupName").value("관심"))
                .andExpect(jsonPath("$.data[0].items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].items[0].nameKr").value("애플"))
                .andExpect(jsonPath("$.data[0].items[0].marketCode").value("NAS"));
    }

    @Test
    @DisplayName("POST /stock-watch/groups — 그룹명을 서비스로 전달한다")
    void createGroup() throws Exception {
        given(stockWatchService.createGroup(1L, "미국 기술주")).willReturn(
                new StockWatchServiceDto.GroupInfo(11L, "미국 기술주", 1, List.of()));

        mockMvc.perform(post("/api/v1/stock-watch/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\":\"미국 기술주\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(11))
                .andExpect(jsonPath("$.data.groupName").value("미국 기술주"));
    }

    @Test
    @DisplayName("POST /stock-watch/groups/{id}/items — 심볼과 시장(선택)을 서비스로 전달한다")
    void addItem() throws Exception {
        given(stockWatchService.addItem(1L, 10L, "AAPL", null)).willReturn(appleItem());

        mockMvc.perform(post("/api/v1/stock-watch/groups/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.countryCode").value("US"));

        verify(stockWatchService).addItem(eq(1L), eq(10L), eq("AAPL"), isNull());
    }

    @Test
    @DisplayName("DELETE /stock-watch/items/{id} — 로그인 사용자로 제거를 위임한다")
    void removeItem() throws Exception {
        mockMvc.perform(delete("/api/v1/stock-watch/items/5"))
                .andExpect(status().isOk());

        verify(stockWatchService).removeItem(1L, 5L);
    }
}
