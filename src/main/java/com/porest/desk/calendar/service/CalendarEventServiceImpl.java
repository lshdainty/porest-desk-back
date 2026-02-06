package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarEventServiceImpl implements CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CalendarEventServiceDto.EventInfo createEvent(CalendarEventServiceDto.CreateCommand command) {
        log.debug("캘린더 이벤트 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        if (command.startDate().isAfter(command.endDate())) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        CalendarEvent event = CalendarEvent.createEvent(
            user,
            command.title(),
            command.description(),
            command.eventType(),
            command.color(),
            command.startDate(),
            command.endDate(),
            command.isAllDay()
        );

        calendarEventRepository.save(event);
        log.info("캘린더 이벤트 등록 완료: eventId={}, userRowId={}", event.getRowId(), command.userRowId());

        return CalendarEventServiceDto.EventInfo.from(event);
    }

    @Override
    public List<CalendarEventServiceDto.EventInfo> getEvents(Long userRowId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("캘린더 이벤트 목록 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        List<CalendarEvent> events = calendarEventRepository.findByUserAndDateRange(userRowId, startDate, endDate);

        return events.stream()
            .map(CalendarEventServiceDto.EventInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public CalendarEventServiceDto.EventInfo updateEvent(Long eventId, CalendarEventServiceDto.UpdateCommand command) {
        log.debug("캘린더 이벤트 수정 시작: eventId={}", eventId);

        CalendarEvent event = findEventOrThrow(eventId);

        if (command.startDate().isAfter(command.endDate())) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        event.updateEvent(
            command.title(),
            command.description(),
            command.eventType(),
            command.color(),
            command.startDate(),
            command.endDate(),
            command.isAllDay()
        );

        log.info("캘린더 이벤트 수정 완료: eventId={}", eventId);

        return CalendarEventServiceDto.EventInfo.from(event);
    }

    @Override
    @Transactional
    public void deleteEvent(Long eventId) {
        log.debug("캘린더 이벤트 삭제 시작: eventId={}", eventId);

        CalendarEvent event = findEventOrThrow(eventId);
        event.deleteEvent();

        log.info("캘린더 이벤트 삭제 완료: eventId={}", eventId);
    }

    private CalendarEvent findEventOrThrow(Long eventId) {
        return calendarEventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.warn("캘린더 이벤트 조회 실패 - 존재하지 않는 이벤트: eventId={}", eventId);
                return new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND);
            });
    }
}
