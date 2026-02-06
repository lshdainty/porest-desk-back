package com.porest.desk.dashboard.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.dashboard.controller.dto.DashboardApiDto;
import com.porest.desk.dashboard.service.DashboardService;
import com.porest.desk.dashboard.service.dto.DashboardServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardApiController {
    private final DashboardService dashboardService;

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardApiDto.SummaryResponse> getDashboardSummary(
            @LoginUser UserPrincipal loginUser) {
        DashboardServiceDto.DashboardSummary summary = dashboardService.getDashboardSummary(loginUser.getRowId());
        return ApiResponse.success(DashboardApiDto.SummaryResponse.from(summary));
    }
}
