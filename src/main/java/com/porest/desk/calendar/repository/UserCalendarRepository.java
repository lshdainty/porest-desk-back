package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.UserCalendar;

import java.util.List;
import java.util.Optional;

public interface UserCalendarRepository {
    Optional<UserCalendar> findById(Long rowId);
    List<UserCalendar> findAllByUser(Long userRowId);
    Optional<UserCalendar> findDefaultByUser(Long userRowId);
    UserCalendar save(UserCalendar entity);
}
