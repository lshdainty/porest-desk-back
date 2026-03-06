package com.porest.desk.calendar.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calendar.controller.dto.UserCalendarApiDto;
import com.porest.desk.calendar.service.UserCalendarService;
import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserCalendarApiController {
    private final UserCalendarService userCalendarService;

    @PostMapping("/calendar/calendars")
    public ApiResponse<UserCalendarApiDto.Response> createCalendar(
            @LoginUser UserPrincipal loginUser,
            @RequestBody UserCalendarApiDto.CreateRequest request) {
        UserCalendarServiceDto.CalendarInfo info = userCalendarService.createCalendar(new UserCalendarServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.calendarName(),
            request.color()
        ));
        return ApiResponse.success(UserCalendarApiDto.Response.from(info));
    }

    @GetMapping("/calendar/calendars")
    public ApiResponse<UserCalendarApiDto.ListResponse> getCalendars(
            @LoginUser UserPrincipal loginUser) {
        List<UserCalendarServiceDto.CalendarInfo> infos = userCalendarService.getCalendars(loginUser.getRowId());
        return ApiResponse.success(UserCalendarApiDto.ListResponse.from(infos));
    }

    @PutMapping("/calendar/calendars/{id}")
    public ApiResponse<UserCalendarApiDto.Response> updateCalendar(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody UserCalendarApiDto.UpdateRequest request) {
        UserCalendarServiceDto.CalendarInfo info = userCalendarService.updateCalendar(id, loginUser.getRowId(), new UserCalendarServiceDto.UpdateCommand(
            request.calendarName(),
            request.color()
        ));
        return ApiResponse.success(UserCalendarApiDto.Response.from(info));
    }

    @PatchMapping("/calendar/calendars/{id}/visibility")
    public ApiResponse<UserCalendarApiDto.Response> toggleVisibility(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        UserCalendarServiceDto.CalendarInfo info = userCalendarService.toggleVisibility(id, loginUser.getRowId());
        return ApiResponse.success(UserCalendarApiDto.Response.from(info));
    }

    @DeleteMapping("/calendar/calendars/{id}")
    public ApiResponse<Void> deleteCalendar(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        userCalendarService.deleteCalendar(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
