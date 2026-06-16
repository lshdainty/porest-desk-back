package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.domain.UserCalendarMember;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.EventLabelRepository;
import com.porest.desk.calendar.repository.EventReminderRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.service.dto.EventReminderServiceDto;
import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarEventServiceImpl implements CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final EventLabelRepository eventLabelRepository;
    private final EventReminderRepository eventReminderRepository;
    private final UserCalendarRepository userCalendarRepository;
    private final UserCalendarService userCalendarService;
    private final UserRepository userRepository;
    private final CalendarMembershipValidator calendarMembershipValidator;

    @Override
    @Transactional
    public CalendarEventServiceDto.EventInfo createEvent(CalendarEventServiceDto.CreateCommand command) {
        log.debug("캘린더 이벤트 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        if (command.startDate().isAfter(command.endDate())) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        EventLabel label = null;
        if (command.labelRowId() != null) {
            label = eventLabelRepository.findById(command.labelRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_LABEL_NOT_FOUND));
            validateLabelOwnership(label, command.userRowId());
        }

        UserCalendar calendar;
        if (command.calendarRowId() != null) {
            calendar = userCalendarRepository.findById(command.calendarRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND));
            // 공유 캘린더면 편집가능(EDIT) 이상만 일정 생성 가능 (읽기전용 차단)
            calendarMembershipValidator.validateCanWrite(command.calendarRowId(), command.userRowId());
        } else {
            UserCalendarServiceDto.CalendarInfo defaultInfo = userCalendarService.getOrCreateDefault(command.userRowId());
            calendar = userCalendarRepository.findById(defaultInfo.rowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND));
        }

        CalendarEvent event = CalendarEvent.createEvent(
            user,
            command.title(),
            command.description(),
            command.eventType(),
            command.color(),
            command.startDate(),
            command.endDate(),
            command.isAllDay(),
            label,
            command.location(),
            command.rrule(),
            calendar
        );
        calendarEventRepository.save(event);

        List<EventReminderServiceDto.ReminderInfo> reminderInfos = new ArrayList<>();
        if (command.reminderMinutes() != null && !command.reminderMinutes().isEmpty()) {
            for (Integer minutes : command.reminderMinutes()) {
                EventReminder reminder = EventReminder.create(event, "NOTIFICATION", minutes);
                eventReminderRepository.save(reminder);
                reminderInfos.add(EventReminderServiceDto.ReminderInfo.from(reminder));
            }
        }

        log.info("캘린더 이벤트 등록 완료: eventId={}, userRowId={}", event.getRowId(), command.userRowId());
        return CalendarEventServiceDto.EventInfo.from(event, reminderInfos);
    }

    @Override
    public List<CalendarEventServiceDto.EventInfo> getEvents(Long userRowId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("캘린더 이벤트 목록 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        // 접근 가능한(소유 + 공유받은) 모든 캘린더의 이벤트
        List<Long> calendarIds = calendarMembershipValidator.getAccessibleCalendarIds(userRowId);
        List<CalendarEvent> events = calendarEventRepository.findByCalendarIdsAndDateRange(calendarIds, startDate, endDate);

        List<Long> eventIds = events.stream().map(CalendarEvent::getRowId).toList();
        Map<Long, List<EventReminderServiceDto.ReminderInfo>> remindersMap = loadRemindersMap(eventIds);

        return events.stream()
            .map(event -> CalendarEventServiceDto.EventInfo.from(
                event,
                remindersMap.getOrDefault(event.getRowId(), List.of())
            ))
            .toList();
    }

    @Override
    @Transactional
    public CalendarEventServiceDto.EventInfo updateEvent(Long eventId, Long userRowId, CalendarEventServiceDto.UpdateCommand command) {
        log.debug("캘린더 이벤트 수정 시작: eventId={}", eventId);

        CalendarEvent event = findEventOrThrow(eventId);
        validateEventOwnership(event, userRowId);

        if (command.startDate().isAfter(command.endDate())) {
            throw new InvalidValueException(DeskErrorCode.CALENDAR_INVALID_DATE_RANGE);
        }

        EventLabel label = null;
        if (command.labelRowId() != null) {
            label = eventLabelRepository.findById(command.labelRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_LABEL_NOT_FOUND));
            validateLabelOwnership(label, userRowId);
        }

        event.updateEvent(
            command.title(),
            command.description(),
            command.eventType(),
            command.color(),
            command.startDate(),
            command.endDate(),
            command.isAllDay(),
            label,
            command.location(),
            command.rrule()
        );

        if (command.calendarRowId() != null) {
            UserCalendar calendar = userCalendarRepository.findById(command.calendarRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND));
            // 옮기려는 캘린더에 쓰기 권한 필요
            calendarMembershipValidator.validateCanWrite(command.calendarRowId(), userRowId);
            event.setCalendar(calendar);
        }

        List<EventReminderServiceDto.ReminderInfo> reminderInfos = new ArrayList<>();
        if (command.reminderMinutes() != null) {
            eventReminderRepository.deleteByEventId(eventId);
            for (Integer minutes : command.reminderMinutes()) {
                EventReminder reminder = EventReminder.create(event, "NOTIFICATION", minutes);
                eventReminderRepository.save(reminder);
                reminderInfos.add(EventReminderServiceDto.ReminderInfo.from(reminder));
            }
        } else {
            List<EventReminder> existing = eventReminderRepository.findByEventId(eventId);
            reminderInfos = existing.stream()
                .map(EventReminderServiceDto.ReminderInfo::from)
                .toList();
        }

        log.info("캘린더 이벤트 수정 완료: eventId={}", eventId);
        return CalendarEventServiceDto.EventInfo.from(event, reminderInfos);
    }

    @Override
    @Transactional
    public void deleteEvent(Long eventId, Long userRowId) {
        log.debug("캘린더 이벤트 삭제 시작: eventId={}", eventId);

        CalendarEvent event = findEventOrThrow(eventId);
        validateEventOwnership(event, userRowId);
        event.deleteEvent();
        eventReminderRepository.deleteByEventId(eventId);

        log.info("캘린더 이벤트 삭제 완료: eventId={}", eventId);
    }

    /**
     * 이벤트는 항상 캘린더에 소속 — 캘린더 멤버십+권한으로 판정.
     * 이벤트 생성자 본인이거나 EDIT 이상 권한이면 수정/삭제 가능.
     */
    private void validateEventOwnership(CalendarEvent event, Long userRowId) {
        // 오래된 데이터에서 생성자(user)가 삭제돼 null 일 수 있으므로 null-safe 하게 소유자 id 추출.
        Long ownerRowId = event.getUser() != null ? event.getUser().getRowId() : null;
        if (event.getCalendar() != null) {
            UserCalendarMember member = calendarMembershipValidator.validateMembership(
                event.getCalendar().getRowId(), userRowId);
            if (!calendarMembershipValidator.canEditOrDelete(member, ownerRowId, userRowId)) {
                throw new ForbiddenException(DeskErrorCode.CALENDAR_EVENT_ACCESS_DENIED);
            }
            return;
        }
        // 캘린더 미소속 이벤트(이론상 없음): 생성자만 (생성자 불명이면 접근 거부)
        if (!userRowId.equals(ownerRowId)) {
            throw new ForbiddenException(DeskErrorCode.CALENDAR_EVENT_ACCESS_DENIED);
        }
    }

    /**
     * 라벨은 사용자 개인 분류값 — 본인 소유 라벨만 이벤트에 부착 가능.
     * (남의 라벨 부착 시 타인 라벨명/색상이 EventInfo 로 노출되는 정보 누출 차단)
     */
    private void validateLabelOwnership(EventLabel label, Long userRowId) {
        Long ownerRowId = label.getUser() != null ? label.getUser().getRowId() : null;
        if (!userRowId.equals(ownerRowId)) {
            log.warn("이벤트 라벨 소유권 검증 실패 - labelId={}, ownerRowId={}, requestUserRowId={}",
                label.getRowId(), ownerRowId, userRowId);
            throw new ForbiddenException(DeskErrorCode.EVENT_LABEL_ACCESS_DENIED);
        }
    }

    private CalendarEvent findEventOrThrow(Long eventId) {
        return calendarEventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.warn("캘린더 이벤트 조회 실패 - 존재하지 않는 이벤트: eventId={}", eventId);
                return new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND);
            });
    }

    private Map<Long, List<EventReminderServiceDto.ReminderInfo>> loadRemindersMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventReminderRepository.findByEventIds(eventIds).stream()
            .map(EventReminderServiceDto.ReminderInfo::from)
            .collect(Collectors.groupingBy(EventReminderServiceDto.ReminderInfo::eventRowId));
    }
}
