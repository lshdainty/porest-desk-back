package com.porest.desk.timer.repository;

import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.type.TimerType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimerSessionRepository {
    Optional<TimerSession> findById(Long rowId);
    List<TimerSession> findByUser(Long userRowId, TimerType timerType, LocalDate startDate, LocalDate endDate);
    List<TimerSession> findDailyStats(Long userRowId, LocalDate startDate, LocalDate endDate);
    TimerSession save(TimerSession timerSession);
    void delete(TimerSession timerSession);
}
