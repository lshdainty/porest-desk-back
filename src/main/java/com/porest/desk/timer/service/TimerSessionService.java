package com.porest.desk.timer.service;

import com.porest.desk.timer.service.dto.TimerSessionServiceDto;
import com.porest.desk.timer.type.TimerType;

import java.time.LocalDate;
import java.util.List;

public interface TimerSessionService {
    TimerSessionServiceDto.SessionInfo createSession(TimerSessionServiceDto.CreateCommand command);
    List<TimerSessionServiceDto.SessionInfo> getSessions(Long userRowId, TimerType timerType, LocalDate startDate, LocalDate endDate);
    List<TimerSessionServiceDto.SessionInfo> getDailyStats(Long userRowId, LocalDate startDate, LocalDate endDate);
    void deleteSession(Long sessionId, Long userRowId);
}
