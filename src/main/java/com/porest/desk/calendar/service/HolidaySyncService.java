package com.porest.desk.calendar.service;

import com.porest.desk.calendar.service.dto.HolidaySyncResult;

import java.util.List;

public interface HolidaySyncService {

    /** 당해 연도부터 {@code app.holiday.sync.lookahead-years} 년 뒤까지 동기화한다. */
    List<HolidaySyncResult> syncUpcoming();

    /** 지정 구간을 연도별로 동기화한다. 초기 백필용. */
    List<HolidaySyncResult> syncRange(int startYear, int endYear);
}
