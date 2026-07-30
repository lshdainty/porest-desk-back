package com.porest.desk.calendar.client;

import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.type.HolidaySource;

import java.util.List;

/** 외부 공휴일 소스. 구현체별로 응답을 {@link ExternalHoliday} 로 정규화해 돌려준다. */
public interface HolidayProvider {

    /** 이 소스가 적재하는 데이터의 출처. */
    HolidaySource source();

    /**
     * 지금 이 소스를 쓸 수 있는지. 인증정보가 없는 등 호출 자체가 무의미하면 false 를 돌려
     * 동기화가 조용히 다음 소스로 넘어가게 한다.
     *
     * <p>조건부 빈({@code @ConditionalOnProperty})으로는 걸러낼 수 없다. 인증키는
     * {@code ${KASI_SERVICE_KEY:}} 처럼 빈 문자열 기본값으로 바인딩되는데, Spring 은 값이
     * {@code "false"} 가 아니면 "프로퍼티 있음"으로 판정해 빈을 만들어 버린다.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 해당 연도의 공휴일 전체를 조회한다.
     *
     * @return 공휴일 목록. 소스에 해당 연도 데이터가 없으면 빈 목록
     * @throws com.porest.desk.calendar.exception.HolidayProviderException 호출·파싱 실패 시
     */
    List<ExternalHoliday> fetch(int year);
}
