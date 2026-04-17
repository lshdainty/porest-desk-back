package com.porest.desk.card.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.card.controller.dto.CardPerformanceApiDto;
import com.porest.desk.card.service.CardPerformanceService;
import com.porest.desk.card.service.dto.CardPerformanceServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CardPerformanceApiController {
    private final CardPerformanceService cardPerformanceService;

    @GetMapping("/card-performance")
    public ApiResponse<CardPerformanceApiDto.PerformanceResponse> getCardPerformance(
        @LoginUser UserPrincipal loginUser,
        @RequestParam Long assetRowId,
        @RequestParam String yearMonth
    ) {
        YearMonth ym = YearMonth.parse(yearMonth);
        CardPerformanceServiceDto.PerformanceInfo info = cardPerformanceService.getPerformance(
            new CardPerformanceServiceDto.PerformanceQuery(loginUser.getRowId(), assetRowId, ym)
        );
        return ApiResponse.success(CardPerformanceApiDto.PerformanceResponse.from(info));
    }
}
