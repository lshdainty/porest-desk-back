package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.UserCalendar;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.repository.UserCalendarRepository;
import com.porest.desk.calendar.service.dto.UserCalendarServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserCalendarServiceImpl implements UserCalendarService {
    private final UserCalendarRepository userCalendarRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserCalendarServiceDto.CalendarInfo createCalendar(UserCalendarServiceDto.CreateCommand command) {
        log.debug("사용자 캘린더 생성 시작: userRowId={}", command.userRowId());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        List<UserCalendar> existing = userCalendarRepository.findAllByUser(command.userRowId());
        int nextOrder = existing.size();
        boolean shouldBeDefault = existing.isEmpty();

        UserCalendar calendar = UserCalendar.createCalendar(user, command.calendarName(), command.color(), nextOrder, shouldBeDefault);
        userCalendarRepository.save(calendar);

        log.info("사용자 캘린더 생성 완료: calendarId={}", calendar.getRowId());
        return UserCalendarServiceDto.CalendarInfo.from(calendar);
    }

    @Override
    public List<UserCalendarServiceDto.CalendarInfo> getCalendars(Long userRowId) {
        log.debug("사용자 캘린더 목록 조회: userRowId={}", userRowId);
        return userCalendarRepository.findAllByUser(userRowId).stream()
            .map(UserCalendarServiceDto.CalendarInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public UserCalendarServiceDto.CalendarInfo updateCalendar(Long calendarId, Long userRowId, UserCalendarServiceDto.UpdateCommand command) {
        log.debug("사용자 캘린더 수정 시작: calendarId={}", calendarId);

        UserCalendar calendar = userCalendarRepository.findById(calendarId)
            .orElseThrow(() -> {
                log.warn("사용자 캘린더 조회 실패: calendarId={}", calendarId);
                return new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND);
            });

        validateCalendarOwnership(calendar, userRowId);

        calendar.updateCalendar(command.calendarName(), command.color());
        log.info("사용자 캘린더 수정 완료: calendarId={}", calendarId);
        return UserCalendarServiceDto.CalendarInfo.from(calendar);
    }

    @Override
    @Transactional
    public UserCalendarServiceDto.CalendarInfo toggleVisibility(Long calendarId, Long userRowId) {
        log.debug("사용자 캘린더 표시 전환 시작: calendarId={}", calendarId);

        UserCalendar calendar = userCalendarRepository.findById(calendarId)
            .orElseThrow(() -> {
                log.warn("사용자 캘린더 조회 실패: calendarId={}", calendarId);
                return new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND);
            });

        validateCalendarOwnership(calendar, userRowId);

        calendar.toggleVisibility();
        log.info("사용자 캘린더 표시 전환 완료: calendarId={}", calendarId);
        return UserCalendarServiceDto.CalendarInfo.from(calendar);
    }

    @Override
    @Transactional
    public void deleteCalendar(Long calendarId, Long userRowId) {
        log.debug("사용자 캘린더 삭제 시작: calendarId={}", calendarId);

        UserCalendar calendar = userCalendarRepository.findById(calendarId)
            .orElseThrow(() -> {
                log.warn("사용자 캘린더 조회 실패: calendarId={}", calendarId);
                return new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND);
            });

        validateCalendarOwnership(calendar, userRowId);

        if (calendar.getIsDefault() == com.porest.core.type.YNType.Y) {
            throw new InvalidValueException(DeskErrorCode.USER_CALENDAR_DEFAULT_DELETE);
        }

        // 삭제할 캘린더의 이벤트를 기본 캘린더로 이동
        UserCalendar defaultCalendar = userCalendarRepository.findDefaultByUser(calendar.getUser().getRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_CALENDAR_NOT_FOUND));

        List<CalendarEvent> events = calendarEventRepository.findByCalendarId(calendarId);
        for (CalendarEvent event : events) {
            event.setCalendar(defaultCalendar);
        }

        calendar.deleteCalendar();
        log.info("사용자 캘린더 삭제 완료: calendarId={}, movedEvents={}", calendarId, events.size());
    }

    private void validateCalendarOwnership(UserCalendar calendar, Long userRowId) {
        if (!calendar.getUser().getRowId().equals(userRowId)) {
            log.warn("캘린더 소유권 검증 실패 - calendarId={}, ownerRowId={}, requestUserRowId={}",
                calendar.getRowId(), calendar.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.CALENDAR_ACCESS_DENIED);
        }
    }

    @Override
    @Transactional
    public UserCalendarServiceDto.CalendarInfo getOrCreateDefault(Long userRowId) {
        log.debug("기본 캘린더 조회 또는 생성: userRowId={}", userRowId);

        return userCalendarRepository.findDefaultByUser(userRowId)
            .map(UserCalendarServiceDto.CalendarInfo::from)
            .orElseGet(() -> {
                User user = userRepository.findById(userRowId)
                    .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

                UserCalendar defaultCalendar = UserCalendar.createCalendar(user, "내 캘린더", "#3b82f6", 0, true);
                userCalendarRepository.save(defaultCalendar);

                log.info("기본 캘린더 생성 완료: calendarId={}, userRowId={}", defaultCalendar.getRowId(), userRowId);
                return UserCalendarServiceDto.CalendarInfo.from(defaultCalendar);
            });
    }
}
