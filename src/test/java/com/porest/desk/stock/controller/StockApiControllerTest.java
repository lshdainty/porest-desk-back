package com.porest.desk.stock.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.stock.repository.StockMasterSearchCondition;
import com.porest.desk.stock.service.StockMasterService;
import com.porest.desk.stock.service.dto.StockServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 종목 검색 API 슬라이스 테스트.
 *
 * <p>구독 게이트 없이 로그인만으로 여는 공개 검색 — 쿼리 파라미터→검색조건 매핑과
 * 페이지 응답 변환을 검증한다. 서비스는 mock.
 */
@WebMvcTest(controllers = StockApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class StockApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StockMasterService stockMasterService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private StockServiceDto.StockInfo apple() {
        return new StockServiceDto.StockInfo(
                1L, "US", StockMarket.NAS, "AAPL", null,
                "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
    }

    @Test
    @DisplayName("GET /stocks — 쿼리 파라미터가 검색조건으로 매핑되고 페이지 응답 반환")
    void searchStocks() throws Exception {
        Page<StockServiceDto.StockInfo> page = new PageImpl<>(List.of(apple()), Pageable.ofSize(20), 1);
        given(stockMasterService.search(any(StockMasterSearchCondition.class), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/stocks")
                        .param("keyword", "애플")
                        .param("countryCode", "US")
                        .param("securityType", "STOCK")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.content[0].nameKr").value("애플"))
                .andExpect(jsonPath("$.data.content[0].marketCode").value("NAS"))
                .andExpect(jsonPath("$.data.content[0].countryCode").value("US"))
                .andExpect(jsonPath("$.data.content[0].currency").value("USD"));

        ArgumentCaptor<StockMasterSearchCondition> captor =
                ArgumentCaptor.forClass(StockMasterSearchCondition.class);
        verify(stockMasterService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().keyword()).isEqualTo("애플");
        assertThat(captor.getValue().countryCode()).isEqualTo("US");
        assertThat(captor.getValue().securityType()).isEqualTo(StockSecurityType.STOCK);
    }

    @Test
    @DisplayName("GET /stocks — 파라미터 없이 호출하면 빈 조건으로 전체 페이징 조회한다")
    void searchStocksWithoutParams() throws Exception {
        Page<StockServiceDto.StockInfo> page = new PageImpl<>(List.of(apple()), Pageable.ofSize(20), 1);
        given(stockMasterService.search(any(StockMasterSearchCondition.class), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.totalElements").value(1));

        ArgumentCaptor<StockMasterSearchCondition> captor =
                ArgumentCaptor.forClass(StockMasterSearchCondition.class);
        verify(stockMasterService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().keyword()).isNull();
        assertThat(captor.getValue().countryCode()).isNull();
        assertThat(captor.getValue().securityType()).isNull();
    }
}
