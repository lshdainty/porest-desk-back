package com.porest.desk.calendar.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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

    // ── 조회 구간 기준 전개 (QA #35) ─────────────────────────────────────────
    // 예전에는 시작일부터 회차를 세며 1200 회에서 잘라, 3년 전 시작한 매일 반복이
    // 이번 달 달력에서 조용히 사라졌다. 아래는 그 재현과 경계 케이스들이다.

    private static final LocalDateTime SEP_START = at(9, 1);
    private static final LocalDateTime SEP_END = LocalDateTime.of(2026, 9, 30, 23, 59);

    @Test
    @DisplayName("3년 전 시작한 매일 반복 — 이번 달 30일이 전부 나온다 (QA #35 재현)")
    void dailyStartedThreeYearsAgoFillsThisMonth() {
        var out = expand("FREQ=DAILY",
            LocalDateTime.of(2023, 6, 1, 0, 0), LocalDateTime.of(2023, 6, 1, 23, 59),
            SEP_START, SEP_END);
        assertThat(out).hasSize(30);
        assertThat(out.get(0).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(out.get(29).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @ParameterizedTest(name = "{0}년 시작")
    @ValueSource(ints = {2022, 2020, 1900})
    @DisplayName("QA 가 '2026년에 전혀 안 보인다' 고 적은 시작연도들 — 전부 30일이 나오고 빠르다")
    void dailyStartedLongAgoStillAppears(int startYear) {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var out = expand("FREQ=DAILY",
                LocalDateTime.of(startYear, 1, 1, 0, 0), LocalDateTime.of(startYear, 1, 1, 23, 59),
                SEP_START, SEP_END);
            assertThat(out).hasSize(30);
            assertThat(out.get(0).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(out.get(29).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        });
    }

    @Test
    @DisplayName("몇 년 전 시작한 매주 반복 — 점프해도 요일이 안 밀린다")
    void weeklyStartedYearsAgoKeepsWeekday() {
        var base = LocalDateTime.of(2020, 1, 1, 0, 0);   // 수요일
        var out = expand("FREQ=WEEKLY", base, base, SEP_START, SEP_END);
        assertThat(out).extracting(o -> o.startDate().toLocalDate().getDayOfMonth())
            .containsExactly(2, 9, 16, 23, 30);
        assertThat(out).allSatisfy(o ->
            assertThat(o.startDate().getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY));
    }

    @Test
    @DisplayName("몇 년 전 시작한 매월 31일 — 점프해도 말일 클램프와 31일 복원이 그대로다")
    void monthlyStartedYearsAgoKeepsEndOfMonthClampAfterJump() {
        var base = LocalDateTime.of(2020, 1, 31, 0, 0);

        var february = expand("FREQ=MONTHLY", base, base,
            LocalDateTime.of(2027, 2, 1, 0, 0), LocalDateTime.of(2027, 2, 28, 23, 59));
        assertThat(february).hasSize(1);
        assertThat(february.get(0).startDate().toLocalDate()).isEqualTo(LocalDate.of(2027, 2, 28));

        var march = expand("FREQ=MONTHLY", base, base,
            LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 31, 23, 59));
        assertThat(march).hasSize(1);
        assertThat(march.get(0).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("몇 년 전 시작한 매년 윤일 — 점프해도 평년엔 2/28 로 내려간다")
    void yearlyLeapStartedYearsAgoClamps() {
        var base = LocalDateTime.of(2016, 2, 29, 9, 0);
        var out = expand("FREQ=YEARLY", base, base,
            LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.of(2026, 2, 28, 23, 59));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).startDate()).isEqualTo(LocalDateTime.of(2026, 2, 28, 9, 0));
    }

    @Test
    @DisplayName("구간보다 먼저 시작한 여러 날짜 발생도 걸치면 남는다 — 점프가 앞질러선 안 된다")
    void multiDayOccurrenceStartingBeforeRangeStillOverlaps() {
        // 5일짜리 원본이 매일 반복 → 구간 안에 끝나는 발생은 시작이 구간 앞이다.
        var out = expand("FREQ=DAILY", at(9, 1), at(9, 6),
            at(9, 20), LocalDateTime.of(2026, 9, 20, 12, 0));
        assertThat(out).extracting(o -> o.startDate().toLocalDate().getDayOfMonth())
            .containsExactly(15, 16, 17, 18, 19, 20);
    }

    @Test
    @DisplayName("발생 시각이 구간 시작과 정확히 같으면 포함된다 (off-by-one 방지)")
    void boundaryOccurrenceExactlyAtRangeStart() {
        var base = LocalDateTime.of(2023, 6, 1, 10, 0);
        var out = expand("FREQ=DAILY", base, base,
            LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 1, 23, 59));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).startDate()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    @DisplayName("말도 안 되게 넓은 구간은 상한에서 멈춘다 — 무한 전개 방어는 살아 있다")
    void safetyCapBoundsAbsurdRange() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var base = LocalDateTime.of(1900, 1, 1, 0, 0);
            var out = expand("FREQ=DAILY", base, base,
                base, LocalDateTime.of(2099, 12, 31, 23, 59));
            assertThat(out).hasSize(RecurrenceExpander.MAX_OCCURRENCES_IN_RANGE);
        });
    }

    @Test
    @DisplayName("시작일이 구간보다 뒤면 한 건도 안 나온다 — 점프가 과거로 되돌아가지 않는다")
    void baseStartingAfterRangeYieldsNothing() {
        var base = LocalDateTime.of(2027, 3, 10, 9, 0);
        assertThat(expand("FREQ=DAILY", base, base, SEP_START, SEP_END)).isEmpty();
        assertThat(expand("FREQ=WEEKLY", base, base, SEP_START, SEP_END)).isEmpty();
        assertThat(expand("FREQ=MONTHLY", base, base, SEP_START, SEP_END)).isEmpty();
        assertThat(expand("FREQ=YEARLY", base, base, SEP_START, SEP_END)).isEmpty();
    }

    @Test
    @DisplayName("연 뷰(366일) 매일 반복 — 상한에 안 닿고 하루도 안 빠진다")
    void dailyFillsWholeYearViewWithoutHittingCap() {
        var out = expand("FREQ=DAILY",
            LocalDateTime.of(2023, 6, 1, 0, 0), LocalDateTime.of(2023, 6, 1, 23, 59),
            LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59));
        assertThat(out).hasSize(365);
        assertThat(out).hasSizeLessThan(RecurrenceExpander.MAX_OCCURRENCES_IN_RANGE);
        assertThat(out.get(0).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(out.get(364).startDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("상한에 걸려 자를 때는 로그를 남긴다 — 예전처럼 조용히 사라지면 안 된다")
    void hittingCapWarnsInsteadOfSilentlyTruncating() {
        var appender = attachAppender();
        try {
            var base = LocalDateTime.of(1900, 1, 1, 0, 0);
            expand("FREQ=DAILY", base, base, base, LocalDateTime.of(2099, 12, 31, 23, 59));

            assertThat(appender.list)
                .anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.WARN);
                    assertThat(e.getFormattedMessage()).contains("반복 전개 상한 도달");
                });
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    @DisplayName("구간 안 발생이 상한과 딱 맞으면 경고하지 않는다 — 자른 게 없다")
    void exactlyCapManyOccurrencesDoesNotWarn() {
        var appender = attachAppender();
        try {
            // 상한 회차째 발생이 구간 마지막 날이 되게 구간을 자른다.
            var base = LocalDateTime.of(2020, 1, 1, 0, 0);
            var rangeEnd = base.plusDays(RecurrenceExpander.MAX_OCCURRENCES_IN_RANGE - 1L)
                .withHour(23).withMinute(59);
            var out = expand("FREQ=DAILY", base, base, base, rangeEnd);

            assertThat(out).hasSize(RecurrenceExpander.MAX_OCCURRENCES_IN_RANGE);
            assertThat(appender.list).noneSatisfy(e ->
                assertThat(e.getFormattedMessage()).contains("반복 전개 상한 도달"));
        } finally {
            detachAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecurrenceExpander.class);
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(RecurrenceExpander.class)).detachAppender(appender);
    }
}
