package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventLabel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventLabelRepository {
    Optional<EventLabel> findById(Long rowId);
    List<EventLabel> findAllByUser(Long userRowId);
    /** 라벨별 사용 중 일정 수 — labelRowId → count (GROUP BY 1회, 삭제 일정 제외). */
    Map<Long, Long> countEventsByLabel(Long userRowId);
    boolean existsActiveByUserAndName(Long userRowId, String labelName, Long excludeRowId);
    EventLabel save(EventLabel eventLabel);
    void delete(EventLabel eventLabel);
}
