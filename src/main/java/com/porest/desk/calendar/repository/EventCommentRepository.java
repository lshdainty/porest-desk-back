package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventComment;

import java.util.List;
import java.util.Optional;

public interface EventCommentRepository {
    Optional<EventComment> findById(Long rowId);
    List<EventComment> findAllByEvent(Long eventRowId);
    EventComment save(EventComment comment);
}
