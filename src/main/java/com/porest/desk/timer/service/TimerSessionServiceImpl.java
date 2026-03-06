package com.porest.desk.timer.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.timer.domain.TimerSession;
import com.porest.desk.timer.repository.TimerSessionRepository;
import com.porest.desk.timer.service.dto.TimerSessionServiceDto;
import com.porest.desk.timer.type.TimerType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TimerSessionServiceImpl implements TimerSessionService {
    private final TimerSessionRepository timerSessionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TimerSessionServiceDto.SessionInfo createSession(TimerSessionServiceDto.CreateCommand command) {
        log.debug("타이머 세션 등록 시작: userRowId={}, timerType={}", command.userRowId(), command.timerType());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        TimerSession session = TimerSession.createSession(
            user,
            command.timerType(),
            command.label(),
            command.startTime(),
            command.endTime(),
            command.durationSeconds(),
            command.targetSeconds(),
            command.isCompleted(),
            command.laps()
        );

        timerSessionRepository.save(session);
        log.info("타이머 세션 등록 완료: sessionId={}, userRowId={}", session.getRowId(), command.userRowId());

        return TimerSessionServiceDto.SessionInfo.from(session);
    }

    @Override
    public List<TimerSessionServiceDto.SessionInfo> getSessions(Long userRowId, TimerType timerType, LocalDate startDate, LocalDate endDate) {
        log.debug("타이머 세션 목록 조회: userRowId={}, timerType={}", userRowId, timerType);

        List<TimerSession> sessions = timerSessionRepository.findByUser(userRowId, timerType, startDate, endDate);

        return sessions.stream()
            .map(TimerSessionServiceDto.SessionInfo::from)
            .toList();
    }

    @Override
    public List<TimerSessionServiceDto.SessionInfo> getDailyStats(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("타이머 일별 통계 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);

        List<TimerSession> sessions = timerSessionRepository.findDailyStats(userRowId, startDate, endDate);

        return sessions.stream()
            .map(TimerSessionServiceDto.SessionInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userRowId) {
        log.debug("타이머 세션 삭제 시작: sessionId={}", sessionId);

        TimerSession session = findSessionOrThrow(sessionId);
        validateSessionOwnership(session, userRowId);
        timerSessionRepository.delete(session);

        log.info("타이머 세션 삭제 완료: sessionId={}", sessionId);
    }

    private void validateSessionOwnership(TimerSession session, Long userRowId) {
        if (!session.getUser().getRowId().equals(userRowId)) {
            log.warn("타이머 세션 소유권 검증 실패 - sessionId={}, ownerRowId={}, requestUserRowId={}",
                session.getRowId(), session.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.TIMER_ACCESS_DENIED);
        }
    }

    private TimerSession findSessionOrThrow(Long sessionId) {
        return timerSessionRepository.findById(sessionId)
            .orElseThrow(() -> {
                log.warn("타이머 세션 조회 실패 - 존재하지 않는 세션: sessionId={}", sessionId);
                return new EntityNotFoundException(DeskErrorCode.TIMER_SESSION_NOT_FOUND);
            });
    }
}
