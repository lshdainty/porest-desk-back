package com.porest.desk.calendar.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calendar.controller.dto.CalendarEventApiDto;
import com.porest.desk.calendar.service.CalendarEventService;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CalendarEventApiController {
    private final CalendarEventService calendarEventService;

    @PostMapping("/calendar/event")
    public ApiResponse<CalendarEventApiDto.Response> createEvent(
            @LoginUser UserPrincipal loginUser,
            @RequestBody CalendarEventApiDto.CreateRequest request) {
        CalendarEventServiceDto.EventInfo info = calendarEventService.createEvent(new CalendarEventServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.title(),
            request.description(),
            request.eventType(),
            request.color(),
            request.startDate(),
            request.endDate(),
            request.isAllDay(),
            request.labelRowId(),
            request.location(),
            request.rrule(),
            request.reminderMinutes(),
            request.calendarRowId(),
            request.groupRowId()
        ));
        return ApiResponse.success(CalendarEventApiDto.Response.from(info));
    }

    @GetMapping("/calendar/events")
    public ApiResponse<CalendarEventApiDto.ListResponse> getEvents(
            @LoginUser UserPrincipal loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<CalendarEventServiceDto.EventInfo> infos = calendarEventService.getEvents(
            loginUser.getRowId(), startDate, endDate
        );
        return ApiResponse.success(CalendarEventApiDto.ListResponse.from(infos));
    }

    @PutMapping("/calendar/event/{id}")
    public ApiResponse<CalendarEventApiDto.Response> updateEvent(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody CalendarEventApiDto.UpdateRequest request) {
        CalendarEventServiceDto.EventInfo info = calendarEventService.updateEvent(id, loginUser.getRowId(), new CalendarEventServiceDto.UpdateCommand(
            request.title(),
            request.description(),
            request.eventType(),
            request.color(),
            request.startDate(),
            request.endDate(),
            request.isAllDay(),
            request.labelRowId(),
            request.location(),
            request.rrule(),
            request.reminderMinutes(),
            request.calendarRowId(),
            request.groupRowId()
        ));
        return ApiResponse.success(CalendarEventApiDto.Response.from(info));
    }

    @GetMapping("/group/{groupId}/calendar/events")
    public ApiResponse<CalendarEventApiDto.ListResponse> getGroupEvents(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<CalendarEventServiceDto.EventInfo> infos = calendarEventService.getGroupEvents(
            loginUser.getRowId(), groupId, startDate, endDate
        );
        return ApiResponse.success(CalendarEventApiDto.ListResponse.from(infos));
    }

    @DeleteMapping("/calendar/event/{id}")
    public ApiResponse<Void> deleteEvent(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        calendarEventService.deleteEvent(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
