package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.UserCalendarMember;

import java.util.List;
import java.util.Optional;

public interface UserCalendarMemberRepository {
    Optional<UserCalendarMember> findById(Long rowId);
    Optional<UserCalendarMember> findByCalendarAndUser(Long calendarRowId, Long userRowId);
    List<UserCalendarMember> findAllByCalendar(Long calendarRowId);
    /** 사용자가 멤버(소유자 포함)인 모든 캘린더 id 목록 — 접근 가능 캘린더 판정용. */
    List<Long> findCalendarIdsByUser(Long userRowId);
    UserCalendarMember save(UserCalendarMember member);
}
