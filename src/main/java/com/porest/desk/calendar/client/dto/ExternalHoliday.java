package com.porest.desk.calendar.client.dto;

import com.porest.desk.calendar.type.HolidayType;

import java.time.LocalDate;

/**
 * 외부 공휴일 소스에서 내려온 공휴일 1건. 소스별 응답 포맷을 흡수한 뒤의 정규화된 형태다.
 *
 * @param holidayDate 공휴일 날짜
 * @param holidayName desk 표기로 정규화된 공휴일 이름
 * @param holidayType 공휴일 유형
 */
public record ExternalHoliday(
    LocalDate holidayDate,
    String holidayName,
    HolidayType holidayType
) {
    /** 동기화 대조 키. 같은 날 두 공휴일이 겹칠 수 있어(예: 2025-05-05 어린이날·석가탄신일) 이름까지 묶는다. */
    public String key() {
        return holidayDate + "|" + holidayName;
    }
}
