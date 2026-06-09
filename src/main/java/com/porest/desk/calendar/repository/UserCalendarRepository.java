package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.UserCalendar;

import java.util.List;
import java.util.Optional;

public interface UserCalendarRepository {
    Optional<UserCalendar> findById(Long rowId);
    List<UserCalendar> findAllByUser(Long userRowId);
    /** 사용자가 멤버(소유+공유받음)인 캘린더 목록. 주어진 id 집합으로 조회. */
    List<UserCalendar> findAllByIds(List<Long> calendarRowIds);
    Optional<UserCalendar> findDefaultByUser(Long userRowId);
    Optional<UserCalendar> findByInviteCode(String inviteCode);
    UserCalendar save(UserCalendar entity);
}
