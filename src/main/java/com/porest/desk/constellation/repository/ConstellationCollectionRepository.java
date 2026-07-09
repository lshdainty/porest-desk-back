package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.ConstellationCollection;

import java.time.LocalDate;
import java.util.List;

public interface ConstellationCollectionRepository {
    /** 별자리별 수집 통계 (도감용) — 수집 이력 있는 별자리만 반환. */
    List<CollectionStat> findStatsByUser(Long userRowId);

    /** 누적 수집 수 (나의 밤하늘 헤더). */
    long countByUser(Long userRowId);

    ConstellationCollection save(ConstellationCollection collection);

    /** 별자리별 수집 횟수 + 마지막 수집일 프로젝션. */
    record CollectionStat(Long constellationRowId, long count, LocalDate lastCollectedDate) {}
}
