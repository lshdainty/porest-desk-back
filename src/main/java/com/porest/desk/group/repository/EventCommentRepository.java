package com.porest.desk.group.repository;

import com.porest.desk.group.domain.EventComment;

import java.util.List;
import java.util.Optional;

public interface EventCommentRepository {
    Optional<EventComment> findById(Long rowId);
    List<EventComment> findAllByEvent(Long eventRowId);
    EventComment save(EventComment comment);
}
