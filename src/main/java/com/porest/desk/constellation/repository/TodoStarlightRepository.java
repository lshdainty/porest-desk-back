package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.TodoStarlight;
import com.porest.desk.constellation.type.StarlightSourceType;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface TodoStarlightRepository {
    /**
     * 같은 출처의 적립 이력 존재 여부 — 회수(soft delete)된 행 포함.
     * true 면 평생 1회 정책에 따라 재적립 불가.
     */
    boolean existsBySourceIncludingRevoked(StarlightSourceType sourceType, Long sourceRowId);

    /** 유효(미회수) 원장 단건 — 회수 대상 조회용. */
    Optional<TodoStarlight> findActiveBySource(StarlightSourceType sourceType, Long sourceRowId);

    /** 해당 일자의 유효 메모 적립 건수 — 일 한도(2) 검사용. */
    long countActiveMemoEarns(Long userRowId, LocalDate earnDate);

    /** 해당 일자의 유효 별빛 합계 (sourceType → points 합). /today 내역 표시용. */
    Map<StarlightSourceType, Integer> sumActivePointsByDate(Long userRowId, LocalDate earnDate);

    TodoStarlight save(TodoStarlight starlight);
}
