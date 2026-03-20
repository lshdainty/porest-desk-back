package com.porest.desk.calendar.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calendar.controller.dto.HolidayApiDto;
import com.porest.desk.calendar.service.HolidayService;
import com.porest.desk.calendar.service.dto.HolidayServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HolidayApiController {
    private final HolidayService holidayService;

    @PostMapping("/holiday")
    public ApiResponse<HolidayApiDto.Response> createHoliday(
            @LoginUser UserPrincipal loginUser,
            @RequestBody HolidayApiDto.CreateRequest request) {
        HolidayServiceDto.HolidayInfo info = holidayService.createHoliday(new HolidayServiceDto.CreateCommand(
            request.holidayDate(),
            request.holidayName(),
            request.holidayType(),
            request.isRecurring()
        ));
        return ApiResponse.success(HolidayApiDto.Response.from(info));
    }

    @GetMapping("/holidays")
    public ApiResponse<HolidayApiDto.ListResponse> getHolidays(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<HolidayServiceDto.HolidayInfo> infos = holidayService.getHolidays(startDate, endDate);
        return ApiResponse.success(HolidayApiDto.ListResponse.from(infos));
    }

    @PutMapping("/holiday/{id}")
    public ApiResponse<HolidayApiDto.Response> updateHoliday(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody HolidayApiDto.UpdateRequest request) {
        HolidayServiceDto.HolidayInfo info = holidayService.updateHoliday(id, new HolidayServiceDto.UpdateCommand(
            request.holidayDate(),
            request.holidayName(),
            request.holidayType(),
            request.isRecurring()
        ));
        return ApiResponse.success(HolidayApiDto.Response.from(info));
    }

    @DeleteMapping("/holiday/{id}")
    public ApiResponse<Void> deleteHoliday(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        holidayService.deleteHoliday(id);
        return ApiResponse.success();
    }
}
