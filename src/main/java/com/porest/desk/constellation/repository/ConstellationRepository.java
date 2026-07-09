package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.Constellation;

import java.util.List;
import java.util.Optional;

public interface ConstellationRepository {
    Optional<Constellation> findById(Long rowId);
    Optional<Constellation> findByKey(String constellationKey);
    /** 일일 목표 순환 대상 (is_active=Y), sort_order 오름차순. */
    List<Constellation> findAllActive();
    /** 도감 표시용 전체 (삭제 제외), sort_order 오름차순. */
    List<Constellation> findAll();
    Constellation save(Constellation constellation);
}
