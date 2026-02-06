package com.porest.desk.timer.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.type.TimerType;

import java.time.LocalDateTime;

public class TimerSessionServiceDto {

    public record CreateCommand(
        Long userRowId,
        TimerType timerType,
        String label,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationSeconds,
        Long targetSeconds,
        YNType isCompleted,
        String laps
    ) {}

    public record SessionInfo(
        Long rowId,
        Long userRowId,
        TimerType timerType,
        String label,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationSeconds,
        Long targetSeconds,
        YNType isCompleted,
        String laps,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static SessionInfo from(TimerSession session) {
            return new SessionInfo(
                session.getRowId(),
                session.getUser().getRowId(),
                session.getTimerType(),
                session.getLabel(),
                session.getStartTime(),
                session.getEndTime(),
                session.getDurationSeconds(),
                session.getTargetSeconds(),
                session.getIsCompleted(),
                session.getLaps(),
                session.getCreateAt(),
                session.getModifyAt()
            );
        }
    }
}
