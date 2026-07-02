package com.porest.desk.card.controller;

import com.porest.core.type.YNType;
import com.porest.core.util.MessageResolver;
import com.porest.desk.card.repository.CardCatalogSearchCondition;
import com.porest.desk.card.service.CardCatalogService;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardCatalog API 슬라이스 테스트.
 *
 * <p>공개 카탈로그 조회 엔드포인트 — {@code @LoginUser} 를 쓰지 않지만 슬라이스 로드를 위해
 * 동일한 {@link WebConfig}/{@link WithLoginUser} 셋업을 유지한다. 서비스는 mock —
 * 컨트롤러의 쿼리 파라미터→검색조건 매핑·페이지네이션 응답·상세 변환을 검증한다.
 */
@WebMvcTest(controllers = CardCatalogApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CardCatalogApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CardCatalogService cardCatalogService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private CardCatalogServiceDto.CatalogSummary sampleSummary() {
        return new CardCatalogServiceDto.CatalogSummary(
                7L, 900L,
                new CardCatalogServiceDto.CompanyInfo(3L, "현대카드", "Hyundai", "logo.png"),
                "The Green", CardType.CREDIT, CardBenefitType.POINT,
                YNType.N, YNType.N, LocalDate.of(2024, 1, 1),
                "img.png", "detail.html",
                new CardCatalogServiceDto.AnnualFeeInfo(300000, "30만원"),
                new CardCatalogServiceDto.PerformanceInfo(500000, "50만원", YNType.Y));
    }

    @Test
    @DisplayName("GET /card-catalogs — 쿼리 파라미터가 검색조건으로 매핑되고 페이지 응답 반환")
    void searchCardCatalogs() throws Exception {
        Page<CardCatalogServiceDto.CatalogSummary> page =
                new PageImpl<>(List.of(sampleSummary()), Pageable.ofSize(20), 1);
        given(cardCatalogService.search(any(CardCatalogSearchCondition.class), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/card-catalogs")
                        .param("keyword", "green")
                        .param("cardType", "CREDIT")
                        .param("benefitType", "POINT")
                        .param("includeDiscontinued", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rowId").value(7))
                .andExpect(jsonPath("$.data.content[0].cardName").value("The Green"))
                .andExpect(jsonPath("$.data.content[0].company.name").value("현대카드"))
                .andExpect(jsonPath("$.data.meta.totalElements").value(1));

        var captor = ArgumentCaptor.forClass(CardCatalogSearchCondition.class);
        verify(cardCatalogService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().keyword()).isEqualTo("green");
        assertThat(captor.getValue().cardType()).isEqualTo(CardType.CREDIT);
        assertThat(captor.getValue().benefitType()).isEqualTo(CardBenefitType.POINT);
        assertThat(captor.getValue().includeDiscontinued()).isTrue();
    }

    @Test
    @DisplayName("GET /card-catalogs — 파라미터 없으면 검색조건 필드 모두 null")
    void searchCardCatalogs_noParams() throws Exception {
        given(cardCatalogService.search(any(CardCatalogSearchCondition.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), Pageable.ofSize(20), 0));

        mockMvc.perform(get("/api/v1/card-catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.totalElements").value(0));

        var captor = ArgumentCaptor.forClass(CardCatalogSearchCondition.class);
        verify(cardCatalogService).search(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().keyword()).isNull();
        assertThat(captor.getValue().cardType()).isNull();
        assertThat(captor.getValue().benefitType()).isNull();
        assertThat(captor.getValue().includeDiscontinued()).isNull();
    }

    @Test
    @DisplayName("GET /card-catalogs/{id} — id로 상세 조회 위임")
    void getCardCatalog() throws Exception {
        CardCatalogServiceDto.CatalogDetail detail = new CardCatalogServiceDto.CatalogDetail(
                sampleSummary(), List.of("VISA"), List.of(), List.of(), List.of(), List.of());
        given(cardCatalogService.getDetail(7L)).willReturn(detail);

        mockMvc.perform(get("/api/v1/card-catalogs/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.rowId").value(7))
                .andExpect(jsonPath("$.data.summary.cardName").value("The Green"))
                .andExpect(jsonPath("$.data.brands[0]").value("VISA"));

        verify(cardCatalogService).getDetail(7L);
    }

    @Test
    @DisplayName("GET /card-catalogs — 잘못된 enum 파라미터 → 400")
    void searchCardCatalogs_invalidEnum() throws Exception {
        mockMvc.perform(get("/api/v1/card-catalogs").param("cardType", "NOPE"))
                .andExpect(status().isBadRequest());
    }
}
