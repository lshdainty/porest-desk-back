package com.porest.desk.card.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 카드 한 장의 회차별 결제일 — 결제일을 바꿔도 이미 정해진 회차의 결제일은 그대로다(D5).
 *
 * <p>회차 k 는 한 달(1일~말일)이고 그 결제일은 <b>다음 달</b>의 결제일(그 달에 없는 날이면 말일)이다.
 * 어느 결제일을 쓰는지는 {@code asset_payment_day_history} 가 정한다 — 회차 시작일 이하인 가장 늦은
 * {@code effective_from_period} 의 결제일. 이력이 없는 카드는 자산의 결제일 하나로 모든 회차를 센다.
 *
 * <p>결제일이 아예 없는 카드(D8 이전에 만든 카드)는 회차가 닫히지 않는다 — 늘 열린 회차(당월)로 본다.
 *
 * @param entries     효력 시작 회차 오름차순
 * @param fallbackDay 이력이 없을 때 쓰는 결제일(자산의 지금 결제일), 없으면 null
 */
public record PaymentSchedule(List<Entry> entries, Integer fallbackDay) {

    /** 이 회차(청구 기간 시작일)부터 이 결제일. */
    public record Entry(LocalDate effectiveFrom, int day) {}

    public PaymentSchedule {
        entries = entries == null ? List.of()
            : entries.stream().sorted(Comparator.comparing(Entry::effectiveFrom)).toList();
    }

    /** 결제일 하나로 모든 회차를 세는 카드. */
    public static PaymentSchedule of(Integer day) {
        return new PaymentSchedule(List.of(), day);
    }

    /** 결제일이 정해져 있는가 — 없으면 회차가 닫히지 않는다(D8). */
    public boolean hasDay() {
        return fallbackDay != null || !entries.isEmpty();
    }

    /** 그 회차에 적용되는 결제일. 결제일이 없는 카드면 null. */
    public Integer dayFor(LocalDate cycleStart) {
        Integer day = null;
        for (Entry e : entries) {
            if (!e.effectiveFrom().isAfter(cycleStart)) {
                day = e.day();
            }
        }
        if (day != null) {
            return day;
        }
        // 첫 이력보다 이른 회차 — 첫 이력의 결제일(카드 등록 전 회차도 같은 결제일로 본다).
        // Integer.valueOf 로 감싼다 — int 와 섞인 삼항은 fallbackDay 를 언박싱해 결제일 없는 카드에서 NPE 가 난다.
        return entries.isEmpty() ? fallbackDay : Integer.valueOf(entries.get(0).day());
    }

    /** 회차의 결제일 — 다음 달의 결제일, 그 달에 없는 날이면 말일. 결제일이 없는 카드면 null. */
    public LocalDate paymentDateOf(LocalDate cycleStart) {
        Integer day = dayFor(cycleStart);
        if (day == null) {
            return null;
        }
        LocalDate next = cycleStart.withDayOfMonth(1).plusMonths(1);
        return next.withDayOfMonth(Math.min(day, next.lengthOfMonth()));
    }

    /** 닫힌 회차 — 결제일이 된 회차(당일 포함, D2). 결제일 없는 카드는 늘 열려 있다. */
    public boolean isClosed(LocalDate cycleStart, LocalDate today) {
        LocalDate d = paymentDateOf(cycleStart);
        return d != null && !d.isAfter(today);
    }

    /** 결제일이 오늘보다 뒤인 첫 회차의 결제일 — 화면의 "다가오는 결제". 결제일 없는 카드면 null. */
    public LocalDate firstOpenPaymentDate(LocalDate today) {
        if (!hasDay()) {
            return null;
        }
        // 이번 달에 결제되는 회차(지난달분)부터 본다 — 회차마다 결제일이 다음 달이라 달이 곧 순서다.
        LocalDate cycle = today.withDayOfMonth(1).minusMonths(1);
        for (int i = 0; i < 3; i++) {
            LocalDate d = paymentDateOf(cycle);
            if (d.isAfter(today)) {
                return d;
            }
            cycle = cycle.plusMonths(1);
        }
        return paymentDateOf(cycle);
    }

    /** 그 결제일 다음 회차의 결제일 — 화면의 "그 다음 회차". */
    public LocalDate followingPaymentDate(LocalDate paymentDate) {
        return paymentDateOf(cycleOfPaymentDate(paymentDate).plusMonths(1));
    }

    /** 결제일이 속한 회차의 시작일 — 결제일의 전달 1일. */
    public static LocalDate cycleOfPaymentDate(LocalDate paymentDate) {
        return paymentDate.withDayOfMonth(1).minusMonths(1);
    }

    /** 오늘이 결제일인 회차의 시작일 — 자정 배치가 결제할 회차. 없으면 null. */
    public LocalDate cycleDueOn(LocalDate today) {
        if (!hasDay()) {
            return null;
        }
        LocalDate cycle = today.withDayOfMonth(1).minusMonths(1);
        return today.equals(paymentDateOf(cycle)) ? cycle : null;
    }

    /**
     * 이 날짜 이하 거래는 닫힌 회차 — 결제일이 오늘 이하인 가장 최근 회차의 말일.
     * 결제일 없는 카드면 null.
     */
    public LocalDate closedThrough(LocalDate today) {
        if (!hasDay()) {
            return null;
        }
        LocalDate cycle = today.withDayOfMonth(1).minusMonths(1);
        if (!isClosed(cycle, today)) {
            cycle = cycle.minusMonths(1);
        }
        return cycle.withDayOfMonth(cycle.lengthOfMonth());
    }
}
