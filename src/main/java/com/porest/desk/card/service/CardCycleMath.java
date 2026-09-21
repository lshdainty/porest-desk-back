package com.porest.desk.card.service;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 카드 회차 규칙의 <b>순수 계산</b> — 닫힌 회차 규칙(2026-09-21 확정) R1~R8.
 *
 * <p>회차 k 는 한 달(1일~말일)이고 그 결제일 D<sub>k</sub> 는 다음 달의 결제일(말일 보정)이다.
 * 결제일이 오늘보다 <b>앞</b>이면 그 회차는 닫혔다(R1) — 당일은 아직 열려 있다.
 *
 * <p>DB 를 모른다. 금액은 호출자가 넘기고, 이 클래스는 "무엇이 기록용이 되고 무엇을 돌려줄
 * 후보인지" 만 정한다. 돌려줄 금액의 상한(크레딧)은 돈의 기록을 봐야 해서 호출자가 잰다.
 */
public final class CardCycleMath {

    private CardCycleMath() {}

    public static LocalDate cycleStartOf(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public static LocalDate cycleEndOf(LocalDate cycleStart) {
        return cycleStart.withDayOfMonth(cycleStart.lengthOfMonth());
    }

    /** 회차의 결제일 — 다음 달의 결제일, 그 달에 없는 날이면 말일. */
    public static LocalDate paymentDateOf(LocalDate cycleStart, int paymentDay) {
        LocalDate next = cycleStart.withDayOfMonth(1).plusMonths(1);
        return next.withDayOfMonth(Math.min(paymentDay, next.lengthOfMonth()));
    }

    /** R1 — 결제일 <b>다음 날부터</b> 닫힌다. 결제일 당일은 열린 회차다. */
    public static boolean isClosed(LocalDate cycleStart, int paymentDay, LocalDate today) {
        return paymentDateOf(cycleStart, paymentDay).isBefore(today);
    }

    /** 결제일이 됐는가(당일 포함) — 자정 배치가 이 회차를 이미 결제했다. */
    public static boolean isBilled(LocalDate cycleStart, int paymentDay, LocalDate today) {
        return !paymentDateOf(cycleStart, paymentDay).isAfter(today);
    }

    /** R3 — 오늘이 이 회차의 결제일인가. */
    public static boolean isPaymentToday(LocalDate cycleStart, int paymentDay, LocalDate today) {
        return paymentDateOf(cycleStart, paymentDay).equals(today);
    }

    /** R6 — 환급 기한: 결제일이 속한 달의 말일(포함). */
    public static LocalDate refundableUntil(LocalDate cycleStart, int paymentDay) {
        LocalDate d = paymentDateOf(cycleStart, paymentDay);
        return d.withDayOfMonth(d.lengthOfMonth());
    }

    public static boolean refundWindowOpen(LocalDate cycleStart, int paymentDay, LocalDate today) {
        return !today.isAfter(refundableUntil(cycleStart, paymentDay));
    }

    /** 표식이 이 회차를 덮는가 — 덮으면 그 회차분은 기록용이다. */
    public static boolean isRecordOnly(LocalDate cycleStart, LocalDate mark) {
        return mark != null && !mark.isBefore(cycleEndOf(cycleStart));
    }

    /**
     * 거래가 회차마다 얹는 금액 — 일시불은 거래 날짜의 회차 하나, 할부는 회차분(R5).
     *
     * <p>지출만 센다. 수입(캐시백)은 회차 청구를 깎는 쪽이라 이 규칙의 대상이 아니다.
     * 할부 회차분은 {@link Expense#installmentAmountAt(int)} 로 센다 — 청구 계산과 같은
     * 산식이어야 기록용 금액과 청구에서 빠지는 금액이 갈리지 않는다.
     */
    public static Map<LocalDate, Long> duesByCycle(Expense e) {
        Map<LocalDate, Long> dues = new TreeMap<>();
        if (e == null || e.getExpenseType() != ExpenseType.EXPENSE
                || e.getExpenseDate() == null || e.getAmount() == null) {
            return dues;
        }
        LocalDate first = cycleStartOf(e.getExpenseDate().toLocalDate());
        if (!e.isInstallment()) {
            dues.put(first, e.getAmount());
            return dues;
        }
        for (int seq = 1; seq <= e.getInstallmentMonths(); seq++) {
            long due = e.installmentAmountAt(seq);
            if (due != 0L) {
                dues.merge(first.plusMonths(seq - 1L), due, Long::sum);
            }
        }
        return dues;
    }

    /**
     * 한쪽(수정 전·후) 모습 — 결제일이 있는 신용카드의 지출일 때만 회차 규칙이 선다.
     *
     * @param dues 회차 시작일 → 그 회차에 얹는 금액
     * @param mark 기록용 표식(없으면 null)
     */
    public record Side(Long cardRowId, Integer paymentDay, Map<LocalDate, Long> dues, LocalDate mark) {

        public static Side none() {
            return new Side(null, null, Map.of(), null);
        }

        public boolean isCard() {
            return cardRowId != null && paymentDay != null;
        }
    }

    /** 회차 하나에서 빠진 기록용 금액 — 기한 안·결제계좌 있음이라 전액 돌려준다. */
    public record Removal(LocalDate cycleStart, long amount) {}

    /**
     * 회차 하나에서 빠진 <b>앱이 결제한</b> 몫.
     *
     * <p>얼마가 실제로 결제로 덮였는지는 돈의 기록을 봐야 알아서 호출자가 크레딧으로 잰다.
     * 덮인 만큼은 {@code refundable} 이면 돌려주고 아니면 상계로 붙잡는다 — 붙잡지 않으면
     * 카드가 양수가 되어 자정 과납 스윕이 기한과 상관없이 돌려준다. 덮이지 않은 몫(결제가
     * 모자랐던 회차)은 원래 빚이었으니 빚이 줄어들 뿐이다.
     */
    public record PaidRemoval(LocalDate cycleStart, long amount, boolean refundable) {}

    /**
     * 판정 결과.
     *
     * @param newMark             수정 뒤 표식
     * @param settleDeltaBefore   옛 카드의 상계 변화(같은 카드면 새 카드 몫까지 여기 합친다)
     * @param settleDeltaAfter    새 카드의 상계 변화(카드가 바뀔 때만)
     * @param paidRemovals        앱이 결제한 몫이 빠진 회차
     * @param recordRefunds       기록용이 빠져 전액 돌려줄 회차
     * @param recordHeld          기록용이 빠졌지만 돌려주지 않는 금액(기한 지남·계좌 없음) — 상계에 이미 반영
     * @param newRecordAmount     이번 저장으로 새로 기록용이 된 금액(저장 확인 문구용)
     * @param sameDayCycle        결제일 당일 회차에 얹힌 몫이 있으면 그 회차(R3), 없으면 null
     * @param afterBillable       옛 카드 회차별로 수정 뒤에도 <b>앱 청구 대상</b>으로 남는 이 거래의 몫
     *                            — 미리보기가 "바뀐 뒤 청구액" 을 셀 때 쓴다(같은 카드일 때만)
     * @param windowClosedRemoval 환급 기한이 지난 회차에서 빠진 몫이 있다 — 확인 문구용
     */
    public record Plan(LocalDate newMark,
                       long settleDeltaBefore,
                       long settleDeltaAfter,
                       List<PaidRemoval> paidRemovals,
                       List<Removal> recordRefunds,
                       long recordHeld,
                       long newRecordAmount,
                       LocalDate sameDayCycle,
                       Map<LocalDate, Long> afterBillable,
                       boolean windowClosedRemoval) {

        /** 아무것도 안 하는 판정 — 카드가 아닌 거래. */
        public static Plan none() {
            return new Plan(null, 0L, 0L, List.of(), List.of(), 0L, 0L, null, Map.of(), false);
        }
    }

    /**
     * 수정 전 → 후로 무엇이 바뀌는지 정한다. create 는 before=none, delete·환불은 after=none.
     *
     * <p>회차마다 전후 금액을 비교해 빠진 몫과 새로 얹힌 몫을 가른 뒤, <b>옮겨 간 것</b>을
     * 먼저 짝짓는다 — 닫힌 회차끼리 날짜만 옮긴 거래는 돈이 안 움직여야 하기 때문이다(R7).
     * 짝이 안 된 빠진 몫만 환급 후보가 되고, 짝이 안 된 새 몫만 새 기록용·새 청구가 된다.
     *
     * <ul>
     *   <li>새 몫이 닫힌 회차: 옮겨 온 기록용 → 기록용 그대로(상계 유지) · 옮겨 온 결제분 →
     *       기록용이 되지만 상계 없음(옛 회차 결제가 이미 덮었다, R7) · 나머지 → 새 기록용(상계 +, R2)</li>
     *   <li>새 몫이 열린 회차: 기한 지난 결제분이 옮겨 오면 기록용 유지(다시 청구하지 않음, R8) ·
     *       상계로 붙잡아 둔 기록용이 옮겨 오면 정상 청구로(상계 −, 케이스 10) · 상계 없이 기록용이
     *       된 몫(앱이 결제한 돈이 옮겨 다닌 것)은 기록용 유지 — 다시 청구하면 두 번 낸다</li>
     *   <li>짝 없는 빠진 결제분: 호출자가 크레딧으로 덮인 만큼을 돌려주거나(기한 안·계좌 있음) 붙잡는다(R6)</li>
     *   <li>짝 없는 빠진 기록용: 기한 안·계좌 있음 → 전액 환급(상계는 이체의 짝으로 남긴다),
     *       아니면 상계 −</li>
     * </ul>
     *
     * <p>표식은 거래당 하나라 "이 회차까지는 전부 기록용" 만 말할 수 있다. 한 회차 안에서 청구분과
     * 기록용이 갈려야 하면 <b>기록용이 이긴다</b> — 청구에서 빠지는 몫은 상계로 카드 빚에서도
     * 지워 빚이 떠돌지 않게 한다. 두 번 내게 되는 쪽보다 덜 내는 쪽이 되돌리기 쉽다.
     *
     * @param hasAccountBefore 옛 카드에 결제계좌가 있는가 — 없으면 이체가 없다
     * @param backing          옛 카드에 지금 걸린 이 거래의 상계 — 기록용 가운데 상계로 붙잡힌 몫
     */
    public static Plan plan(Side before, Side after, boolean hasAccountBefore, long backing, LocalDate today) {
        boolean sameCard = before.isCard() && after.isCard()
            && before.cardRowId().equals(after.cardRowId());

        // 옮겨 갈 수 있는 몫 — 회차 순서대로 둔다(오래된 것부터 짝짓는다).
        List<long[]> paidPool = new ArrayList<>();    // {epochDay, amount}
        List<long[]> recordPool = new ArrayList<>();
        Map<LocalDate, Long> added = new TreeMap<>();
        TreeSet<LocalDate> recordCycles = new TreeSet<>();
        // 결제일이 아직 안 온 회차에서 앱이 청구할 몫 — 표식이 덮게 되면 상계로 돌린다.
        Map<LocalDate, Long> unbilled = new TreeMap<>();
        // 결제일 전 회차에서 빠진 몫 — 옮겨 간 짝으로 보지 않는다(결제된 돈이 아니다). 선결제가
        // 덮고 있었다면 호출자가 크레딧으로 잰 만큼 돌려준다 — 안 돌려주면 카드가 양수로 떠
        // 과납 스윕이 회차에 안 묶인 환급을 만들고, "이미 낸 돈" 이 줄지 않는다(23차 결함 2).
        List<long[]> unbilledRemoved = new ArrayList<>();

        if (sameCard) {
            TreeSet<LocalDate> cycles = new TreeSet<>(before.dues().keySet());
            cycles.addAll(after.dues().keySet());
            for (LocalDate c : cycles) {
                long b = before.dues().getOrDefault(c, 0L);
                long a = after.dues().getOrDefault(c, 0L);
                boolean wasRecord = isRecordOnly(c, before.mark());
                boolean billed = isBilled(c, before.paymentDay(), today);
                long kept = Math.min(a, b);
                if (kept > 0L && wasRecord) {
                    recordCycles.add(c);
                } else if (kept > 0L && !billed) {
                    unbilled.merge(c, kept, Long::sum);
                }
                if (b > a) {
                    long d = b - a;
                    if (wasRecord) {
                        recordPool.add(new long[] {c.toEpochDay(), d});
                    } else if (billed) {
                        paidPool.add(new long[] {c.toEpochDay(), d});
                    } else {
                        unbilledRemoved.add(new long[] {c.toEpochDay(), d});
                    }
                } else if (a > b) {
                    added.put(c, a - b);
                }
            }
        } else {
            if (before.isCard()) {
                for (Map.Entry<LocalDate, Long> e : before.dues().entrySet()) {
                    LocalDate c = e.getKey();
                    if (isRecordOnly(c, before.mark())) {
                        recordPool.add(new long[] {c.toEpochDay(), e.getValue()});
                    } else if (isBilled(c, before.paymentDay(), today)) {
                        paidPool.add(new long[] {c.toEpochDay(), e.getValue()});
                    } else {
                        unbilledRemoved.add(new long[] {c.toEpochDay(), e.getValue()});
                    }
                }
            }
            if (after.isCard()) {
                added.putAll(after.dues());
            }
        }

        long settleBefore = 0L;
        long settleAfter = 0L;
        long newRecord = 0L;
        long backingLeft = Math.max(0L, backing);
        LocalDate sameDay = null;

        for (Map.Entry<LocalDate, Long> e : added.entrySet()) {
            LocalDate c = e.getKey();
            long remain = e.getValue();
            int day = after.paymentDay();
            if (isClosed(c, day, today)) {
                // 짝짓기는 같은 카드 안에서만 — 다른 카드로 옮긴 돈은 옛 카드에서 빠지고 새로 생긴다.
                long fromRecord = sameCard ? take(recordPool, remain, null) : 0L; // 기록용이 옮겨 옴 — 상계 유지
                remain -= fromRecord;
                long fromPaid = sameCard ? take(paidPool, remain, null) : 0L;     // 결제분이 옮겨 옴 — R7
                remain -= fromPaid;
                if (remain > 0L) {                                     // 새 소급 입력 — R2
                    newRecord += remain;
                    if (sameCard) {
                        settleBefore += remain;
                    } else {
                        settleAfter += remain;
                    }
                }
                recordCycles.add(c);
                continue;
            }
            if (isPaymentToday(c, day, today)) {
                sameDay = c;
            }
            // R8 — 기한 지난 결제분이 열린 회차로 오면 다시 청구하지 않는다.
            long fromExpiredPaid = sameCard
                ? take(paidPool, remain, src -> !refundWindowOpen(src, before.paymentDay(), today))
                : 0L;
            remain -= fromExpiredPaid;
            long fromRecord = sameCard ? take(recordPool, remain, null) : 0L;
            remain -= fromRecord;
            long backed = Math.min(fromRecord, backingLeft);
            if (fromExpiredPaid > 0L || backed < fromRecord || recordCycles.contains(c)) {
                // 이 회차는 기록용으로 남는다 — 함께 얹힌 새 몫은 아래에서 상계로 돌린다.
                recordCycles.add(c);
            } else if (fromRecord > 0L) {
                // 케이스 10 — 상계로 붙잡아 둔 기록용이 열린 회차로 오면 정상 청구, 상계를 걷는다.
                settleBefore -= fromRecord;
                backingLeft -= fromRecord;
            }
            if (remain > 0L) {
                unbilled.merge(c, remain, Long::sum);
            }
        }

        LocalDate newMark = after.isCard() && !recordCycles.isEmpty()
            ? cycleEndOf(recordCycles.last())
            : null;

        // 표식이 덮게 된 청구 전 몫 — 청구에서 빠지니 카드 빚으로도 남지 않게 상계한다.
        if (newMark != null) {
            for (Map.Entry<LocalDate, Long> e : unbilled.entrySet()) {
                if (isRecordOnly(e.getKey(), newMark)) {
                    newRecord += e.getValue();
                    if (sameCard) {
                        settleBefore += e.getValue();
                    } else {
                        settleAfter += e.getValue();
                    }
                }
            }
        }

        Map<LocalDate, Long> afterBillable = new TreeMap<>();
        if (sameCard) {
            for (Map.Entry<LocalDate, Long> e : after.dues().entrySet()) {
                if (!isRecordOnly(e.getKey(), newMark)) {
                    afterBillable.put(e.getKey(), e.getValue());
                }
            }
        }

        List<PaidRemoval> paidRemovals = new ArrayList<>();
        List<Removal> recordRefunds = new ArrayList<>();
        long recordHeld = 0L;
        boolean windowClosed = false;
        for (long[] p : paidPool) {
            if (p[1] <= 0L) {
                continue;
            }
            LocalDate c = LocalDate.ofEpochDay(p[0]);
            boolean open = refundWindowOpen(c, before.paymentDay(), today);
            windowClosed |= !open;
            paidRemovals.add(new PaidRemoval(c, p[1], hasAccountBefore && open));
        }
        for (long[] u : unbilledRemoved) {
            LocalDate c = LocalDate.ofEpochDay(u[0]);
            paidRemovals.add(new PaidRemoval(c, u[1],
                hasAccountBefore && refundWindowOpen(c, before.paymentDay(), today)));
        }
        for (long[] r : recordPool) {
            if (r[1] <= 0L) {
                continue;
            }
            LocalDate c = LocalDate.ofEpochDay(r[0]);
            boolean open = refundWindowOpen(c, before.paymentDay(), today);
            windowClosed |= !open;
            if (hasAccountBefore && open) {
                // 전액 환급 — 상계는 그대로 둬 환급 이체와 짝을 이룬다(카드는 0 유지).
                recordRefunds.add(new Removal(c, r[1]));
            } else {
                settleBefore -= r[1];
                recordHeld += r[1];
            }
        }

        return new Plan(newMark, settleBefore, settleAfter, paidRemovals, recordRefunds, recordHeld,
            newRecord, sameDay, afterBillable, windowClosed);
    }

    /** 풀에서 조건에 맞는 몫을 최대 {@code want} 만큼 꺼낸다. */
    private static long take(List<long[]> pool, long want,
                             java.util.function.Predicate<LocalDate> sourceFilter) {
        long got = 0L;
        for (long[] p : pool) {
            if (got >= want) {
                break;
            }
            if (p[1] <= 0L) {
                continue;
            }
            if (sourceFilter != null && !sourceFilter.test(LocalDate.ofEpochDay(p[0]))) {
                continue;
            }
            long t = Math.min(p[1], want - got);
            p[1] -= t;
            got += t;
        }
        return got;
    }
}
