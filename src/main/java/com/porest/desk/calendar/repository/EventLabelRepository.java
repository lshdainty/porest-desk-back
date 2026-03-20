package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventLabel;

import java.util.List;
import java.util.Optional;

public interface EventLabelRepository {
    Optional<EventLabel> findById(Long rowId);
    List<EventLabel> findAllByUser(Long userRowId);
    EventLabel save(EventLabel eventLabel);
    void delete(EventLabel eventLabel);
}
