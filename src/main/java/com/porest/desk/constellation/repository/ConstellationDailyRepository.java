package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.ConstellationDaily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConstellationDailyRepository {
    Optional<ConstellationDaily> findByUserAndDate(Long userRowId, LocalDate obsDate);

    /** 기간 내 관측 기록 (obs_date 오름차순). 무행일 = REST 는 서비스에서 채움. */
    List<ConstellationDaily> findByUserAndDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate);

    /** 마지막 수집(GROWN) 일자 — 보호권 정산(gap 계산) 기준점. */
    Optional<LocalDate> findLatestGrownDate(Long userRowId);

    ConstellationDaily save(ConstellationDaily daily);
}
