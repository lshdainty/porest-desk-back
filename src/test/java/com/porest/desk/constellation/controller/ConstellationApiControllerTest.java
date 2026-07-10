package com.porest.desk.constellation.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.constellation.service.ConstellationService;
import com.porest.desk.constellation.service.dto.ConstellationServiceDto;
import com.porest.desk.constellation.type.DailyStatus;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 별자리 조회 API 슬라이스 테스트 — 매핑/로그인 사용자 위임/days 클램프/응답 본문 검증.
 */
@WebMvcTest(controllers = ConstellationApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ConstellationApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ConstellationService constellationService;
    @MockitoBean private MessageResolver messageResolver;

    private ConstellationServiceDto.ConstellationInfo dipperInfo() {
        return new ConstellationServiceDto.ConstellationInfo(
            10L, "dipper", "북두칠성", "Big Dipper", "국자 모양 일곱 별", "Seven bright stars", "blue", 7,
            "{\"pts\":[[88,30]],\"edges\":[[0,1]]}", 1);
    }

    @Test
    @DisplayName("GET /constellations — 카탈로그(star_map 포함) 응답")
    void getCatalog() throws Exception {
        given(constellationService.getCatalog()).willReturn(List.of(dipperInfo()));

        mockMvc.perform(get("/api/v1/constellations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.constellations[0].constellationKey").value("dipper"))
            .andExpect(jsonPath("$.data.constellations[0].nameEn").value("Big Dipper"))
            .andExpect(jsonPath("$.data.constellations[0].descriptionEn").value("Seven bright stars"))
            .andExpect(jsonPath("$.data.constellations[0].starCount").value(7))
            .andExpect(jsonPath("$.data.constellations[0].starMap").exists());
    }

    @Test
    @DisplayName("GET /constellations/today — 로그인 사용자 현황 매핑")
    void getToday() throws Exception {
        given(constellationService.getToday(1L)).willReturn(
            new ConstellationServiceDto.TodayInfo(dipperInfo(), 5, 7, false, 4, 1, 3, 1, 32L));

        mockMvc.perform(get("/api/v1/constellations/today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.constellation.constellationKey").value("dipper"))
            .andExpect(jsonPath("$.data.points").value(5))
            .andExpect(jsonPath("$.data.goal").value(7))
            .andExpect(jsonPath("$.data.collected").value(false))
            .andExpect(jsonPath("$.data.todoPoints").value(4))
            .andExpect(jsonPath("$.data.memoPoints").value(1))
            .andExpect(jsonPath("$.data.streak").value(3))
            .andExpect(jsonPath("$.data.guardCount").value(1))
            .andExpect(jsonPath("$.data.totalCollected").value(32));

        verify(constellationService).getToday(1L);
    }

    @Test
    @DisplayName("GET /constellations/sky — 기본 14일, days 파라미터는 1~60 클램프")
    void getSkyClampsDays() throws Exception {
        given(constellationService.getSky(1L, 14)).willReturn(List.of(
            new ConstellationServiceDto.SkyDay(LocalDate.now(), DailyStatus.REST, null, null, 0, false)));
        given(constellationService.getSky(1L, 60)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/constellations/sky"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.days[0].status").value("REST"));
        verify(constellationService).getSky(1L, 14);

        mockMvc.perform(get("/api/v1/constellations/sky").param("days", "999"))
            .andExpect(status().isOk());
        verify(constellationService).getSky(1L, 60);
    }

    @Test
    @DisplayName("GET /constellations/collection — 도감 응답(수집 종수/누적)")
    void getCollection() throws Exception {
        given(constellationService.getCollection(1L)).willReturn(
            new ConstellationServiceDto.CollectionInfo(
                List.of(new ConstellationServiceDto.CollectionEntry(dipperInfo(), 32L, LocalDate.of(2026, 7, 9))),
                1, 32L));

        mockMvc.perform(get("/api/v1/constellations/collection"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.entries[0].collectCount").value(32))
            .andExpect(jsonPath("$.data.collectedKinds").value(1))
            .andExpect(jsonPath("$.data.totalCollected").value(32));
    }

    @Test
    @DisplayName("GET /constellations/sky — days 잘못된 타입이면 400")
    void getSkyInvalidDaysType() throws Exception {
        mockMvc.perform(get("/api/v1/constellations/sky").param("days", "abc"))
            .andExpect(status().isBadRequest());
    }
}
