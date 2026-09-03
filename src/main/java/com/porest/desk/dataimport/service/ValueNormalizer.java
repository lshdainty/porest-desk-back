package com.porest.desk.dataimport.service;

import com.porest.desk.expense.type.ExpenseType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * 원본 셀 문자열 → 도메인 값(날짜/금액/유형) 정규화. static util.
 *
 * <p>날짜: ISO·일반 한국형 포맷 + Excel serial number(편한가계부 xlsx 의 "기간" 열이
 * 1899-12-30 기준 serial)를 모두 허용. 금액: 콤마·통화기호 제거 후 절대값 정수(부호는 유형으로).
 * 유형: 수입/지출/income/expense/입금/출금 등 다국어·동의어 매칭, 이체는 null(스킵 대상).
 */
public final class ValueNormalizer {

    private ValueNormalizer() {}

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /**
     * 달력에 없는 날짜(2026-02-30)를 <b>말없이 당겨 붙이지 않기 위한</b> 해석 방식.
     *
     * <p>{@link DateTimeFormatter#ofPattern} 의 기본값 {@link ResolverStyle#SMART} 는 없는 날을
     * 그 달의 마지막 날로 끌어당긴다 — {@code 2026-02-30 10:00} 이 조용히 {@code 2026-02-28} 로
     * 저장됐다. 같은 값을 거래 API 로 직접 보내면 400 인데 가져오기만 통과시켜,
     * <b>같은 입력이 경로에 따라 다른 결과</b>가 됐다.
     *
     * <p>{@link ResolverStyle#STRICT} 는 없는 날을 거부한다. 다만 STRICT 에서 {@code yyyy}
     * (연호 기준 연도)는 연호 필드를 함께 요구해 정상 날짜까지 터지므로, 패턴의 연도 문자를
     * 전부 {@code uuuu}(ISO 연도)로 바꿔 쓴다.
     */
    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    // ISO_LOCAL_DATE_TIME·ISO_LOCAL_DATE 는 이미 STRICT 다 — 그대로 쓴다.
    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        // 내보내기 CSV 의 형식 — 왕복(내보내기 → 다시 가져오기)이 여기서 깨지면 안 된다.
        strict("uuuu-MM-dd HH:mm[:ss]"),
        strict("uuuu.MM.dd HH:mm[:ss]"),
        strict("uuuu/MM/dd HH:mm[:ss]")
    );

    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm")
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        strict("uuuu.MM.dd"),
        strict("uuuu/MM/dd"),
        strict("MM/dd/uuuu"),
        strict("uuuuMMdd")
    );

    /**
     * 날짜/일시 파싱. 실패 시 null — 호출측({@link ImportColumnMapper})이 {@code reason: date} 실패 행으로 돌린다.
     *
     * <p>달력에 없는 날짜는 <b>실패</b>다. 끌어당겨 저장하면 사용자가 쓴 적 없는 날에 거래가 생기고,
     * 화면에는 아무 경고도 남지 않는다.
     */
    public static LocalDateTime parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        for (DateTimeFormatter f : DATETIME_FORMATS) {
            try { return LocalDateTime.parse(s, f); } catch (Exception ignore) { /* try next */ }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f).atStartOfDay(); } catch (Exception ignore) { /* try next */ }
        }
        // Excel serial number (예: 46242.69784722223)
        try {
            double serial = Double.parseDouble(s);
            if (serial > 0 && serial < 200_000) { // 합리적 범위(≈ 1900~2447)
                long days = (long) Math.floor(serial);
                double frac = serial - days;
                long secondsOfDay = Math.round(frac * 86_400);
                return EXCEL_EPOCH.plusDays(days).atStartOfDay().plusSeconds(secondsOfDay);
            }
        } catch (NumberFormatException ignore) { /* not a number */ }
        return null;
    }

    /** 시각 파싱 — "HH:mm[:ss]". 날짜와 열이 분리된 소스(뱅크샐러드)용. 실패 시 null. */
    public static LocalTime parseTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : TIME_FORMATS) {
            try { return LocalTime.parse(s, f); } catch (Exception ignore) { /* try next */ }
        }
        return null;
    }

    /**
     * 금액 파싱 — 콤마·통화기호·공백 제거 후 절대값 정수. 실패/0 이하 시 null.
     *
     * <p>파일에서 읽은 문자열을 double 로 거치면 소수 금액에 이진 오차가 섞여 원 단위가 어긋난다.
     * 여기서 만든 값이 그대로 거래 금액으로 저장되므로 {@code new BigDecimal(String)} 으로
     * 십진 값을 그대로 받아 반올림한다.
     */
    public static Long parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("[,\\s₩\\$€£¥원]", "");
        s = s.replaceAll("[()]", "").replace("+", "");
        if (s.isEmpty() || s.equals("-") || s.equals(".")) return null;
        try {
            long v = new BigDecimal(s).abs().setScale(0, RoundingMode.HALF_UP).longValueExact();
            return v <= 0 ? null : v;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** 유형 파싱 — INCOME/EXPENSE. 이체·미상 시 null(호출측이 스킵/파생 판단). */
    public static ExpenseType parseType(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return null;
        if (s.contains("수입") || s.contains("income") || s.contains("입금") || s.equals("+")) {
            return ExpenseType.INCOME;
        }
        if (s.contains("지출") || s.contains("expense") || s.contains("출금")
            || s.contains("소비") || s.equals("-")) {
            return ExpenseType.EXPENSE;
        }
        // 이체(transfer/이체) 및 알 수 없는 값 → null. 둘의 구분은 isTransfer 로 한다.
        return null;
    }

    /**
     * 이체 유형인지 — 가계부 거래(수입/지출)가 아니므로 넣지 않지만, <b>오류가 아니라 건너뜀</b>이다.
     * 알 수 없는 값(오타·깨진 데이터)과 구분해야 결과 화면에서 진짜 문제를 가려낼 수 있다.
     */
    public static boolean isTransfer(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase();
        return s.contains("이체") || s.contains("transfer") || s.contains("대체");
    }
}
