package com.porest.desk.card.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.card.service.CardBenefitCategoryMappingService;
import com.porest.desk.card.service.dto.CardBenefitCategoryMappingServiceDto;
import com.porest.desk.card.service.dto.CardCatalogServiceDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardBenefitCategoryMapping API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 매핑·바디 역직렬화·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = CardBenefitCategoryMappingApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class CardBenefitCategoryMappingApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CardBenefitCategoryMappingService cardBenefitCategoryMappingService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private CardBenefitCategoryMappingServiceDto.MappingInfo sampleMapping() {
        return new CardBenefitCategoryMappingServiceDto.MappingInfo(
                10L, "DINING", 100L, "식비", true);
    }

    private CardCatalogServiceDto.BenefitInfo sampleBenefit() {
        return new CardCatalogServiceDto.BenefitInfo(
                5L, "DINING", "utensils", "커피 10% 할인", "요약", "상세 설명", 0);
    }

    @Test
    @DisplayName("GET /card-benefit-mappings — 로그인 사용자로 유효 매핑 목록 조회")
    void getMappings() throws Exception {
        given(cardBenefitCategoryMappingService.getEffectiveMappings(1L))
                .willReturn(List.of(sampleMapping()));

        mockMvc.perform(get("/api/v1/card-benefit-mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mappings[0].rowId").value(10))
                .andExpect(jsonPath("$.data.mappings[0].benefitCategory").value("DINING"))
                .andExpect(jsonPath("$.data.mappings[0].expenseCategoryName").value("식비"))
                .andExpect(jsonPath("$.data.mappings[0].isCustom").value(true));

        verify(cardBenefitCategoryMappingService).getEffectiveMappings(1L);
    }

    @Test
    @DisplayName("POST /card-benefit-mappings — 로그인 사용자·바디로 upsertMapping 위임")
    void createOrUpdateMapping() throws Exception {
        given(cardBenefitCategoryMappingService.upsertMapping(any()))
                .willReturn(sampleMapping());

        String body = """
                {"benefitCategory":"DINING","expenseCategoryRowId":100}
                """;

        mockMvc.perform(post("/api/v1/card-benefit-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value(10))
                .andExpect(jsonPath("$.data.expenseCategoryRowId").value(100));

        var captor = ArgumentCaptor.forClass(CardBenefitCategoryMappingServiceDto.CreateCommand.class);
        verify(cardBenefitCategoryMappingService).upsertMapping(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().benefitCategory()).isEqualTo("DINING");
        assertThat(captor.getValue().expenseCategoryRowId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("DELETE /card-benefit-mappings/{id} — id·로그인 사용자로 삭제 위임")
    void deleteMapping() throws Exception {
        mockMvc.perform(delete("/api/v1/card-benefit-mappings/{id}", 7L))
                .andExpect(status().isOk());

        verify(cardBenefitCategoryMappingService).deleteMapping(eq(7L), eq(1L));
    }

    @Test
    @DisplayName("GET /card-catalogs/{cardRowId}/available-benefits — 로그인 사용자·경로·쿼리로 혜택 조회 위임")
    void getAvailableBenefits() throws Exception {
        given(cardBenefitCategoryMappingService.getAvailableBenefits(1L, 55L, 100L))
                .willReturn(List.of(sampleBenefit()));

        mockMvc.perform(get("/api/v1/card-catalogs/{cardRowId}/available-benefits", 55L)
                        .param("expenseCategoryRowId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rowId").value(5))
                .andExpect(jsonPath("$.data[0].title").value("커피 10% 할인"));

        verify(cardBenefitCategoryMappingService).getAvailableBenefits(1L, 55L, 100L);
    }

    @Test
    @DisplayName("GET /card-catalogs/{cardRowId}/available-benefits — 잘못된 타입 경로변수 → 400")
    void getAvailableBenefits_typeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/card-catalogs/{cardRowId}/available-benefits", "not-a-number")
                        .param("expenseCategoryRowId", "100"))
                .andExpect(status().isBadRequest());
    }
}
