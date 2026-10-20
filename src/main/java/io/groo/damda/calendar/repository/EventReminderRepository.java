package io.groo.damda.calendar.repository;

import io.groo.damda.calendar.domain.EventReminder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventReminderRepository {
    Optional<EventReminder> findById(Long rowId);
    List<EventReminder> findByEventId(Long eventRowId);
    List<EventReminder> findByEventIds(List<Long> eventRowIds);
    List<EventReminder> findUnsentRemindersStartingBefore(LocalDateTime startBound);
    EventReminder save(EventReminder eventReminder);
    void deleteByEventId(Long eventRowId);
    void deleteById(Long rowId);
}
