package com.porest.desk.constellation.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.constellation.controller.dto.ConstellationApiDto;
import com.porest.desk.constellation.service.ConstellationService;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 별자리 게이미피케이션 조회 API — 적립은 할일 완료/메모 작성의 부수효과라 별도 엔드포인트 없음.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConstellationApiController {
    private static final int SKY_DAYS_DEFAULT = 14;
    private static final int SKY_DAYS_MAX = 60;

    private final ConstellationService constellationService;

    /** 별자리 마스터 카탈로그 (star_map 좌표 포함). */
    @GetMapping("/constellations")
    public ApiResponse<ConstellationApiDto.CatalogResponse> getCatalog() {
        return ApiResponse.success(ConstellationApiDto.CatalogResponse.from(constellationService.getCatalog()));
    }

    /** 오늘의 목표 별자리 + 내 별빛 현황(진행/스트릭/보호권/누적). */
    @GetMapping("/constellations/today")
    public ApiResponse<ConstellationApiDto.TodayResponse> getToday(@LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(
            ConstellationApiDto.TodayResponse.from(constellationService.getToday(loginUser.getRowId()))
        );
    }

    /** 나의 밤하늘 — 최근 N일 관측 기록(무행일 REST 포함, 기본 14일). */
    @GetMapping("/constellations/sky")
    public ApiResponse<ConstellationApiDto.SkyResponse> getSky(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(defaultValue = "" + SKY_DAYS_DEFAULT) int days) {
        int clamped = Math.max(1, Math.min(days, SKY_DAYS_MAX));
        return ApiResponse.success(
            ConstellationApiDto.SkyResponse.from(constellationService.getSky(loginUser.getRowId(), clamped))
        );
    }

    /** 별자리 도감 — 전체 별자리 + 수집 횟수/마지막 수집일. */
    @GetMapping("/constellations/collection")
    public ApiResponse<ConstellationApiDto.CollectionResponse> getCollection(@LoginUser UserPrincipal loginUser) {
        return ApiResponse.success(
            ConstellationApiDto.CollectionResponse.from(constellationService.getCollection(loginUser.getRowId()))
        );
    }
}
