package com.porest.desk.calendar.service.dto;

import com.porest.desk.calendar.type.HolidaySource;

/**
 * 연도 1건의 동기화 결과.
 *
 * @param year      동기화 대상 연도
 * @param source    실제로 데이터를 가져온 소스. 모든 소스가 실패했으면 null
 * @param inserted  새로 적재한 건수
 * @param updated   유형이 달라져 갱신한 건수
 * @param removed   외부 소스에서 사라져 삭제 처리한 건수
 * @param unchanged 변경 없이 넘어간 건수(사용자가 지운 뒤 그대로 둔 행 포함)
 */
public record HolidaySyncResult(
    int year,
    HolidaySource source,
    int inserted,
    int updated,
    int removed,
    int unchanged
) {
    public static HolidaySyncResult failed(int year) {
        return new HolidaySyncResult(year, null, 0, 0, 0, 0);
    }

    public boolean isFailed() {
        return source == null;
    }

    /** 실제로 DB 를 건드렸는지. 변경이 있을 때만 로그를 남겨 매일 도는 동기화의 소음을 줄인다. */
    public boolean hasChanges() {
        return inserted > 0 || updated > 0 || removed > 0;
    }
}
