package com.porest.desk.calendar.type;

/**
 * 공휴일 데이터의 출처.
 *
 * <p>{@link #KASI}·{@link #HOLIDAYS_KR} 은 외부 동기화가 관리하는 행이라 매 동기화마다 갱신·삭제될 수 있고,
 * {@link #MANUAL} 은 동기화가 건드리지 않는다.
 */
public enum HolidaySource {
    /** 한국천문연구원 특일 정보 API(공공데이터포털). */
    KASI,
    /** 우주항공청 월력요항 기반 가공 데이터(holidays-kr). KASI 호출 실패 시 폴백. */
    HOLIDAYS_KR,
    /** 수기 등록분. 동기화 대상에서 제외한다. */
    MANUAL
}
