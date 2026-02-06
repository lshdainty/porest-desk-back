package com.porest.desk.timer.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.timer.type.TimerType;
import com.porest.desk.timer.service.dto.TimerSessionServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class TimerSessionApiDto {

    public record CreateRequest(
        TimerType timerType,
        String label,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationSeconds,
        Long targetSeconds,
        YNType isCompleted,
        String laps
    ) {}

    public record Response(
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
        public static Response from(TimerSessionServiceDto.SessionInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.timerType(),
                info.label(),
                info.startTime(),
                info.endTime(),
                info.durationSeconds(),
                info.targetSeconds(),
                info.isCompleted(),
                info.laps(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> sessions
    ) {
        public static ListResponse from(List<TimerSessionServiceDto.SessionInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
