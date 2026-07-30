package com.porest.desk.calendar.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calendar.controller.dto.HolidayApiDto;
import com.porest.desk.calendar.service.HolidayService;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 공휴일 조회 API.
 *
 * <p>공휴일은 스케줄러가 한국천문연구원 특일정보 API 와 매일 맞추므로 쓰기 엔드포인트를 두지 않는다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HolidayApiController {
    private final HolidayService holidayService;

    @GetMapping("/holidays")
    public ApiResponse<HolidayApiDto.ListResponse> getHolidays(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<HolidayServiceDto.HolidayInfo> infos = holidayService.getHolidays(startDate, endDate);
        return ApiResponse.success(HolidayApiDto.ListResponse.from(infos));
    }
}
