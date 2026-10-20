package io.groo.damda.dashboard.service;

import io.groo.damda.dashboard.service.dto.DashboardServiceDto;

public interface DashboardService {
    DashboardServiceDto.DashboardSummary getDashboardSummary(Long userRowId);
    String getDashboardLayout(Long userRowId);
    String updateDashboardLayout(Long userRowId, String dashboard);
}
