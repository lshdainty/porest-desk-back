package com.porest.desk.dashboard.service;

import com.porest.desk.dashboard.service.dto.DashboardServiceDto;

public interface DashboardService {
    DashboardServiceDto.DashboardSummary getDashboardSummary(Long userRowId);
}
