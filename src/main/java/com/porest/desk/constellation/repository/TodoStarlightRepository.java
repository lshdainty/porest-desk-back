package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.TodoStarlight;
import com.porest.desk.constellation.type.StarlightSourceType;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface TodoStarlightRepository {
    /**
     * 같은 출처의 원장 단건 — 회수(soft delete)된 행 포함.
     * 행이 있으면 평생 1회 정책 대상이다(활성이면 차단, 당일 회수분이면 복원).
     */
    Optional<TodoStarlight> findBySourceIncludingRevoked(StarlightSourceType sourceType, Long sourceRowId);

    /** 유효(미회수) 원장 단건 — 회수 대상 조회용. */
    Optional<TodoStarlight> findActiveBySource(StarlightSourceType sourceType, Long sourceRowId);

    /** 해당 일자의 유효 메모 적립 건수 — 일 한도(2) 검사용. */
    long countActiveMemoEarns(Long userRowId, LocalDate earnDate);

    /** 해당 일자의 유효 별빛 합계 (sourceType → points 합). /today 내역 표시용. */
    Map<StarlightSourceType, Integer> sumActivePointsByDate(Long userRowId, LocalDate earnDate);

    TodoStarlight save(TodoStarlight starlight);

    /**
     * 그날의 별빛 합계 — <b>원장에서 집계</b>한다.
     *
     * <p>daily.points 는 원장 합계를 받아 적어 둔 캐시였다. 금액이든 점수든 파생값을
     * 따로 저장하면 어긋난다 — 적립·회수 어느 한쪽을 빠뜨리면 조용히 벌어진다.
     * 하루치라 행 수가 적어 집계가 싸다.
     */
    int sumPointsByUserAndDate(Long userRowId, LocalDate earnDate);
}
