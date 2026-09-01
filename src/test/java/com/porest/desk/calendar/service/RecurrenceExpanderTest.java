package com.porest.desk.calendar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반복 전개 규칙 회귀 방지 — 클라이언트 폼이 만드는 순수 FREQ 4종이 스코프다.
 *
 * <p>dev 실측: FREQ=WEEKLY 로 저장한 일정이 10월 뷰에 10/3 한 번만 떴다
 * (10/10·17·24·31 부재) — 전개가 어디에도 없었다.
 */
@DisplayName("RecurrenceExpander — rrule 발생 전개")
class RecurrenceExpanderTest {

    private static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 0, 0);
    }

    private static List<RecurrenceExpander.Occurrence> expand(
            String rrule, LocalDateTime baseStart, LocalDateTime baseEnd,
            LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return RecurrenceExpander.expand(baseStart, baseEnd, rrule, rangeStart, rangeEnd);
    }

    @Test
    @DisplayName("매주 — 10월 구간에 5회 (버그 재현 케이스)")
    void weeklyExpandsAcrossMonth() {
        var out = expand("FREQ=WEEKLY", at(10, 3), at(10, 3).plusDays(1).minusSeconds(1),
            at(10, 1), at(10, 31).plusDays(1).minusSeconds(1));
        assertThat(out).extracting(o -> o.startDate().toLocalDate().getDayOfMonth())
            .containsExactly(3, 10, 17, 24, 31);
    }

    @Test
    @DisplayName("원본이 구간보다 앞서 시작해도 구간 안 발생이 나온다")
    void baseBeforeRangeStillYieldsOccurrences() {
        var out = expand("FREQ=WEEKLY", at(9, 3), at(9, 3), at(11, 1), at(11, 30));
        assertThat(out).isNotEmpty();
        assertThat(out).allSatisfy(o -> {
            assertThat(o.startDate()).isAfterOrEqualTo(at(11, 1));
            assertThat(o.startDate()).isBeforeOrEqualTo(at(11, 30));
        });
    }

    @Test
    @DisplayName("매월 31일 시작 — 없는 달은 말일로 내려가고, 이후 달은 다시 31일로 돌아온다")
    void monthlyClampsToEndOfShortMonthWithoutDrift() {
        var out = expand("FREQ=MONTHLY", at(1, 31), at(1, 31),
            at(1, 1), LocalDateTime.of(2026, 4, 30, 23, 59));
        assertThat(out).extracting(o -> o.startDate().toLocalDate())
            .containsExactly(
                java.time.LocalDate.of(2026, 1, 31),
                java.time.LocalDate.of(2026, 2, 28),  // 말일 클램프
                java.time.LocalDate.of(2026, 3, 31),  // 누적 오차 없이 복원
                java.time.LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("매일 — 구간 겹침만 남는다")
    void dailyWithinRangeOnly() {
        var out = expand("FREQ=DAILY", at(9, 1), at(9, 1), at(9, 10), at(9, 12));
        assertThat(out).hasSize(3);
    }

    @Test
    @DisplayName("매년 — 윤일(2/29) 시작은 평년에 2/28 로 내려간다")
    void yearlyLeapDayClamps() {
        var base = LocalDateTime.of(2028, 2, 29, 9, 0);
        var out = expand("FREQ=YEARLY", base, base,
            LocalDateTime.of(2029, 1, 1, 0, 0), LocalDateTime.of(2029, 12, 31, 23, 59));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).startDate().toLocalDate()).isEqualTo(java.time.LocalDate.of(2029, 2, 28));
    }

    @Test
    @DisplayName("여러 날짜에 걸친 원본 — 발생마다 지속시간이 따라간다")
    void durationCarriesToOccurrences() {
        var out = expand("FREQ=WEEKLY", at(9, 8), at(9, 12), at(9, 14), at(9, 30));
        assertThat(out.get(0).startDate()).isEqualTo(at(9, 15));
        assertThat(out.get(0).endDate()).isEqualTo(at(9, 19));
    }

    @Test
    @DisplayName("rrule 이 없으면 원본 1회 — 구간 밖이면 0회")
    void noRruleKeepsSingle() {
        assertThat(expand(null, at(9, 1), at(9, 1), at(9, 1), at(9, 30))).hasSize(1);
        assertThat(expand(null, at(8, 1), at(8, 1), at(9, 1), at(9, 30))).isEmpty();
    }

    @Test
    @DisplayName("지원 밖 rrule(확장 파라미터)은 전개하지 않고 원본 1회만")
    void unknownRruleFallsBackToSingle() {
        var out = expand("FREQ=WEEKLY;INTERVAL=2", at(9, 1), at(9, 1), at(9, 1), at(9, 30));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).startDate()).isEqualTo(at(9, 1));
    }
}
