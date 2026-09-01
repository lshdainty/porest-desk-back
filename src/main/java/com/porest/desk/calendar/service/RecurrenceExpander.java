package com.porest.desk.calendar.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 반복 일정(rrule)을 조회 구간의 발생(occurrence) 시각들로 전개한다.
 *
 * <p>지금까지 rrule 은 저장·전달만 되고 어느 쪽도 전개하지 않아, 매주 반복이
 * 첫 회차 한 번만 화면에 남았다(신규 가입 E2E 발견). 전개를 서버에 두는 이유:
 * 웹·앱 두 클라이언트가 같은 결과를 공짜로 받고, 구간 밖 원본을 포함하는 조회
 * 조건과 전개 규칙이 한곳에 붙어 있어야 어긋나지 않는다.
 *
 * <p>지원 범위는 <b>클라이언트 폼이 만드는 값 그대로</b>다 — 순수
 * {@code FREQ=DAILY|WEEKLY|MONTHLY|YEARLY} (INTERVAL·UNTIL·COUNT 없음, 웹·앱
 * EventForm 공통). 그 밖의 rrule 은 전개하지 않고 원본 1회만 돌려준다 — 모르는
 * 규칙을 임의 해석해 엉뚱한 날짜를 만드는 것보다 안전하다.
 *
 * <p>MONTHLY 는 시작일의 "일(day-of-month)" 을 매달 유지하되 그 일이 없는 달은
 * <b>말일로 내린다</b>(1/31 → 2/28) — 반복 거래의 안내문("해당 일이 없는 달은
 * 말일에 처리됩니다")과 같은 규칙. YEARLY 도 2/29 → 2/28 같은 방식이다.
 *
 * <p>발생마다 원본의 지속시간(start~end)이 그대로 따라간다. recurrence_id 기반
 * 예외(한 회차만 수정/삭제)는 아직 만들어지는 곳이 없어 다루지 않는다 — 생기면
 * 여기서 그 날짜의 발생을 빼는 것으로 확장한다.
 */
public final class RecurrenceExpander {

    /** 구간·빈도 계산이 어긋나도 응답이 폭주하지 않게 거는 안전 상한 (일간 3년치보다 크게). */
    private static final int MAX_OCCURRENCES = 1200;

    private RecurrenceExpander() {
    }

    public record Occurrence(LocalDateTime startDate, LocalDateTime endDate) {
    }

    /**
     * [rangeStart, rangeEnd] 와 겹치는 발생들을 시각 순서로 돌려준다.
     * rrule 이 없거나 지원 밖이면 원본 1회(구간과 겹칠 때)만 돌려준다.
     */
    public static List<Occurrence> expand(LocalDateTime baseStart, LocalDateTime baseEnd, String rrule,
                                          LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Duration duration = Duration.between(baseStart, baseEnd);
        Freq freq = parse(rrule);
        if (freq == null) {
            return overlaps(baseStart, baseEnd, rangeStart, rangeEnd)
                ? List.of(new Occurrence(baseStart, baseEnd))
                : List.of();
        }

        List<Occurrence> out = new ArrayList<>();
        for (int i = 0; i < MAX_OCCURRENCES; i++) {
            LocalDateTime start = occurrenceStart(baseStart, freq, i);
            if (start.isAfter(rangeEnd)) {
                break;
            }
            LocalDateTime end = start.plus(duration);
            if (overlaps(start, end, rangeStart, rangeEnd)) {
                out.add(new Occurrence(start, end));
            }
        }
        return out;
    }

    /** 폼이 만드는 순수 FREQ 값만 인식한다. 나머지(확장 파라미터 포함)는 null. */
    static Freq parse(String rrule) {
        if (rrule == null) return null;
        return switch (rrule.trim()) {
            case "FREQ=DAILY" -> Freq.DAILY;
            case "FREQ=WEEKLY" -> Freq.WEEKLY;
            case "FREQ=MONTHLY" -> Freq.MONTHLY;
            case "FREQ=YEARLY" -> Freq.YEARLY;
            default -> null;
        };
    }

    private static LocalDateTime occurrenceStart(LocalDateTime base, Freq freq, int n) {
        if (n == 0) return base;
        return switch (freq) {
            case DAILY -> base.plusDays(n);
            case WEEKLY -> base.plusWeeks(n);
            // plusMonths/plusYears 는 없는 일자를 말일로 내린다(1/31+1개월=2/28) —
            // 원하는 규칙 그대로라 별도 클램프가 필요 없다. 단 "31일 시작이 2월을
            // 지나며 28일로 줄어드는" 누적 오차를 피하려고 항상 base 에서 더한다.
            case MONTHLY -> base.plusMonths(n);
            case YEARLY -> base.plusYears(n);
        };
    }

    private static boolean overlaps(LocalDateTime start, LocalDateTime end,
                                    LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return !start.isAfter(rangeEnd) && !end.isBefore(rangeStart);
    }

    enum Freq { DAILY, WEEKLY, MONTHLY, YEARLY }
}
