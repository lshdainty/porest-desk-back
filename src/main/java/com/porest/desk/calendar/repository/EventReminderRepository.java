package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventReminder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventReminderRepository {
    Optional<EventReminder> findById(Long rowId);
    List<EventReminder> findByEventId(Long eventRowId);
    List<EventReminder> findByEventIds(List<Long> eventRowIds);
    List<EventReminder> findUnsentDueReminders(LocalDateTime now);
    EventReminder save(EventReminder eventReminder);
    void deleteByEventId(Long eventRowId);
    void deleteById(Long rowId);
}
