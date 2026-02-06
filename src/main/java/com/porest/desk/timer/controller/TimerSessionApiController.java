package com.porest.desk.timer.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.timer.controller.dto.TimerSessionApiDto;
import com.porest.desk.timer.service.TimerSessionService;
import com.porest.desk.timer.service.dto.TimerSessionServiceDto;
import com.porest.desk.timer.type.TimerType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TimerSessionApiController {
    private final TimerSessionService timerSessionService;

    @PostMapping("/timer/session")
    public ApiResponse<TimerSessionApiDto.Response> createSession(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TimerSessionApiDto.CreateRequest request) {
        TimerSessionServiceDto.SessionInfo info = timerSessionService.createSession(new TimerSessionServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.timerType(),
            request.label(),
            request.startTime(),
            request.endTime(),
            request.durationSeconds(),
            request.targetSeconds(),
            request.isCompleted(),
            request.laps()
        ));
        return ApiResponse.success(TimerSessionApiDto.Response.from(info));
    }

    @GetMapping("/timer/sessions")
    public ApiResponse<TimerSessionApiDto.ListResponse> getSessions(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) TimerType timerType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<TimerSessionServiceDto.SessionInfo> infos = timerSessionService.getSessions(
            loginUser.getRowId(), timerType, startDate, endDate
        );
        return ApiResponse.success(TimerSessionApiDto.ListResponse.from(infos));
    }

    @GetMapping("/timer/sessions/daily-stats")
    public ApiResponse<TimerSessionApiDto.ListResponse> getDailyStats(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<TimerSessionServiceDto.SessionInfo> infos = timerSessionService.getDailyStats(
            loginUser.getRowId(), startDate, endDate
        );
        return ApiResponse.success(TimerSessionApiDto.ListResponse.from(infos));
    }

    @DeleteMapping("/timer/session/{id}")
    public ApiResponse<Void> deleteSession(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        timerSessionService.deleteSession(id);
        return ApiResponse.success();
    }
}
