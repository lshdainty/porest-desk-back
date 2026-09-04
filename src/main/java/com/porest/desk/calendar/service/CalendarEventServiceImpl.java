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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * 지금 서버가 만드는 유일한 알림 종류. {@code event_reminder.reminder_type} 은 컬럼으로 남아 있고
     * 유일성도 이 값을 낀 조합(event_row_id, reminder_type, minutes_before)으로 잡는다 —
     * 나중에 "10분 전 푸시 + 10분 전 메일" 을 넣을 자리를 지금 막지 않기 위해서다.
     */
    private static final String DEFAULT_REMINDER_TYPE = "NOTIFICATION";

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
        for (Integer minutes : distinctReminderMinutes(command.reminderMinutes())) {
            EventReminder reminder = EventReminder.create(event, DEFAULT_REMINDER_TYPE, minutes);
            eventReminderRepository.save(reminder);
            reminderInfos.add(EventReminderServiceDto.ReminderInfo.from(reminder));
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

        // 반복(rrule) 이벤트는 구간 안 발생(occurrence)들로 전개해 내려준다 —
        // 전개가 없으면 매주 반복이 첫 회차 한 번만 화면에 남는다.
        List<CalendarEventServiceDto.EventInfo> result = new java.util.ArrayList<>();
        for (CalendarEvent event : events) {
            CalendarEventServiceDto.EventInfo base = CalendarEventServiceDto.EventInfo.from(
                event, remindersMap.getOrDefault(event.getRowId(), List.of()));
            for (RecurrenceExpander.Occurrence oc : RecurrenceExpander.expand(
                    event.getStartDate(), event.getEndDate(), event.getRrule(), startDate, endDate)) {
                result.add(base.withOccurrence(oc.startDate(), oc.endDate()));
            }
        }
        result.sort(java.util.Comparator.comparing(CalendarEventServiceDto.EventInfo::startDate));
        return result;
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

        List<EventReminderServiceDto.ReminderInfo> reminderInfos;
        if (command.reminderMinutes() != null) {
            reminderInfos = syncReminders(event, command.reminderMinutes());
        } else {
            reminderInfos = eventReminderRepository.findByEventId(eventId).stream()
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
     * 요청이 보낸 알림 분(分) 목록을 <b>저장할 수 있는 형태</b>로 접는다 — null 을 걷어내고 중복을 없앤다.
     *
     * <p>중복을 409 로 되돌리지 않는 이유: "10분 전을 두 번 알려 달라" 는 표현할 수 있는 의도가 아니다.
     * 라벨·태그의 이름 중복은 사용자가 고쳐야 할 입력이지만, 같은 알림 두 개는 요청이 잘못됐다기보다
     * 화면이 같은 줄을 두 번 담은 것에 가깝다. 조용히 하나로 접는 게 사용자가 기대하는 결과다.
     *
     * <p>null 원소를 걷어내는 것도 여기다. {@code minutes_before} 는 DB·엔티티 모두 NOT NULL 이라
     * {@code [null]} 이 그대로 내려가면 500 이 됐다. 컨트롤러 DTO 가 {@code @NotNull} 로 먼저 막지만,
     * 서비스를 직접 부르는 경로(가져오기·집계)까지 덮으려면 이 자리에도 있어야 한다.
     *
     * <p>순서는 요청이 보낸 순서를 유지한다({@link LinkedHashSet}) — 응답의 알림 순서가 요청과
     * 어긋나면 화면이 방금 저장한 줄을 못 찾는다.
     */
    private static List<Integer> distinctReminderMinutes(List<Integer> reminderMinutes) {
        if (reminderMinutes == null || reminderMinutes.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(
            reminderMinutes.stream().filter(Objects::nonNull).toList()));
    }

    /**
     * 알림 세트를 요청대로 맞춘다 — <b>있으면 그 행을 두고, 없는 것만 만들고, 빠진 것만 지운다.</b>
     *
     * <p>종전에는 {@code deleteByEventId} 로 전량을 지우고 다시 넣었다. 결과 세트는 같지만 두 가지가
     * 무너진다.
     * <ul>
     *   <li><b>이미 보낸 알림이 다시 간다.</b> 새로 만든 행은 {@code is_sent='N'} 이라, 일정 제목만
     *       고쳐도 어제 울린 알림이 오늘 또 울린다.</li>
     *   <li><b>row_id 가 매번 바뀐다.</b> 지금은 참조하는 곳이 없지만, 알림 읽음 표시처럼 행을
     *       가리키는 것이 하나라도 붙는 순간 끊긴다.</li>
     * </ul>
     * 지운 알림은 소프트 삭제가 아니라 <b>실제 DELETE</b> 다 — 이 테이블에는 삭제 플래그가 없다.
     */
    private List<EventReminderServiceDto.ReminderInfo> syncReminders(CalendarEvent event, List<Integer> reminderMinutes) {
        List<Integer> wanted = distinctReminderMinutes(reminderMinutes);
        // 같은 (타입, 분) 이 이미 여러 행으로 들어와 있을 수 있다(DB UNIQUE 가 붙기 전에 쌓인 것).
        // 첫 행만 남기고 나머지는 여기서 정리된다 — 두 번째부터는 byMinutes 에 안 담겨 삭제 대상이 된다.
        List<EventReminder> existing = eventReminderRepository.findByEventId(event.getRowId());
        Map<Integer, EventReminder> byMinutes = new LinkedHashMap<>();
        List<EventReminder> stale = new ArrayList<>();
        for (EventReminder reminder : existing) {
            boolean sameType = DEFAULT_REMINDER_TYPE.equals(reminder.getReminderType());
            if (sameType && reminder.getMinutesBefore() != null
                && byMinutes.putIfAbsent(reminder.getMinutesBefore(), reminder) == null) {
                continue;
            }
            stale.add(reminder);
        }

        List<EventReminderServiceDto.ReminderInfo> result = new ArrayList<>(wanted.size());
        for (Integer minutes : wanted) {
            EventReminder reminder = byMinutes.remove(minutes);
            if (reminder == null) {
                reminder = EventReminder.create(event, DEFAULT_REMINDER_TYPE, minutes);
                eventReminderRepository.save(reminder);
            }
            result.add(EventReminderServiceDto.ReminderInfo.from(reminder));
        }
        // 요청에서 빠진 것 + 타입이 다르거나 중복이라 못 담은 것
        stale.addAll(byMinutes.values());
        for (EventReminder reminder : stale) {
            eventReminderRepository.deleteById(reminder.getRowId());
        }
        return result;
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
