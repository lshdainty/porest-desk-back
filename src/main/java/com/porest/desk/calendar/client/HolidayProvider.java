package com.porest.desk.calendar.client;

import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.type.HolidaySource;

import java.util.List;

/** 외부 공휴일 소스. 구현체별로 응답을 {@link ExternalHoliday} 로 정규화해 돌려준다. */
public interface HolidayProvider {

    /** 이 소스가 적재하는 데이터의 출처. */
    HolidaySource source();

    /**
     * 해당 연도의 공휴일 전체를 조회한다.
     *
     * @return 공휴일 목록. 소스에 해당 연도 데이터가 없으면 빈 목록
     * @throws com.porest.desk.calendar.exception.HolidayProviderException 호출·파싱 실패 시
     */
    List<ExternalHoliday> fetch(int year);
}
