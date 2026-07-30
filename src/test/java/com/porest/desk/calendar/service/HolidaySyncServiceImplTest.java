package com.porest.desk.calendar.service;

import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.service.dto.HolidaySyncResult;
import com.porest.desk.calendar.type.HolidaySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 연도 구간 동기화 오케스트레이션 테스트. */
@ExtendWith(MockitoExtension.class)
class HolidaySyncServiceImplTest {

    @Mock private HolidayYearSynchronizer yearSynchronizer;

    private HolidaySyncServiceImpl sut(HolidayProperties properties) {
        return new HolidaySyncServiceImpl(yearSynchronizer, properties);
    }

    private HolidayProperties properties(int lookaheadYears) {
        HolidayProperties properties = new HolidayProperties();
        properties.getSync().setLookaheadYears(lookaheadYears);
        return properties;
    }

    private HolidaySyncResult ok(int year) {
        return new HolidaySyncResult(year, HolidaySource.KASI, 1, 0, 0, 0);
    }

    @Test
    @DisplayName("syncUpcoming 은 당해 연도부터 lookahead 년 뒤까지 동기화한다")
    void syncUpcomingCoversLookahead() {
        int currentYear = LocalDate.now().getYear();
        given(yearSynchronizer.sync(anyInt())).willAnswer(inv -> ok(inv.getArgument(0)));

        List<HolidaySyncResult> results = sut(properties(1)).syncUpcoming();

        assertThat(results).extracting(HolidaySyncResult::year)
                .containsExactly(currentYear, currentYear + 1);
    }

    @Test
    @DisplayName("lookahead 가 0 이면 당해 연도만 동기화한다")
    void syncUpcomingWithoutLookahead() {
        int currentYear = LocalDate.now().getYear();
        given(yearSynchronizer.sync(anyInt())).willAnswer(inv -> ok(inv.getArgument(0)));

        List<HolidaySyncResult> results = sut(properties(0)).syncUpcoming();

        assertThat(results).extracting(HolidaySyncResult::year).containsExactly(currentYear);
    }

    @Test
    @DisplayName("syncRange 는 구간의 모든 연도를 순서대로 동기화한다")
    void syncRangeCoversAllYears() {
        given(yearSynchronizer.sync(anyInt())).willAnswer(inv -> ok(inv.getArgument(0)));

        List<HolidaySyncResult> results = sut(properties(1)).syncRange(2024, 2027);

        assertThat(results).extracting(HolidaySyncResult::year)
                .containsExactly(2024, 2025, 2026, 2027);
    }

    @Test
    @DisplayName("한 해에서 예외가 나도 나머지 연도는 계속 동기화한다")
    void syncRangeIsolatesFailure() {
        given(yearSynchronizer.sync(2024)).willReturn(ok(2024));
        willThrow(new RuntimeException("DB 오류")).given(yearSynchronizer).sync(2025);
        given(yearSynchronizer.sync(2026)).willReturn(ok(2026));

        List<HolidaySyncResult> results = sut(properties(1)).syncRange(2024, 2026);

        assertThat(results).hasSize(3);
        assertThat(results.get(1).isFailed()).isTrue();
        assertThat(results.get(2).isFailed()).isFalse();
    }

    @Test
    @DisplayName("구간이 뒤집혀 있으면 아무것도 하지 않는다")
    void syncRangeIgnoresInvertedRange() {
        List<HolidaySyncResult> results = sut(properties(1)).syncRange(2027, 2024);

        assertThat(results).isEmpty();
        verify(yearSynchronizer, never()).sync(anyInt());
    }
}
