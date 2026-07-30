package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.Holiday;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository {
    List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 해당 연도의 공휴일 전체를 삭제분까지 포함해 조회한다.
     *
     * <p>동기화 대조용. 삭제된 행을 빼고 보면 (날짜, 이름) 유니크 제약에 걸려 재적재가 실패한다.
     */
    List<Holiday> findByYearIncludingDeleted(int year);

    Holiday save(Holiday holiday);
}
