package io.groo.damda.namu.service;

import io.groo.damda.toss.dto.TossStockDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 나무 코드값 → 우리 어휘 표 (13b-0). 서비스가 옮기고, 웹 · 앱은 지금 쓰는 이름표
 * ({@code warning.*} · {@code stocksWarning*})를 그대로 쓴다. 모르는 값은 버린다.
 */
final class NamuMarketCodes {

    /** 토스 어휘에 없는 새 이름 — 나무 "투자유의종목" (이름표 "투자유의"). */
    static final String INVESTMENT_ATTENTION = "INVESTMENT_ATTENTION";

    private NamuMarketCodes() {
    }

    /**
     * 국내 장 상태 {@code market_status}: 0 프리 · 1 정규 · 2 애프터 · A 단일가 · C 일시마감 · D 개장전 ·
     * E · F · X 장 마감. 모르는 값 → null.
     */
    static String krMarketState(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "0" -> "PRE";
            case "1" -> "REGULAR";
            case "2" -> "AFTER";
            case "A" -> "SINGLE_PRICE";
            case "C" -> "HALTED";
            case "D" -> "BEFORE_OPEN";
            case "E", "F", "X" -> "CLOSED";
            default -> null;
        };
    }

    /** 미국 {@code marketperiod_cls}: 0 프리 · 1 정규 · 2 애프터 · 3 주간거래. 모르는 값 → null. */
    static String gbMarketState(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim()) {
            case "0" -> "PRE";
            case "1" -> "REGULAR";
            case "2" -> "AFTER";
            case "3" -> "DAYTIME";
            default -> null;
        };
    }

    /** 미국 시간외 구간 이름 — 프리 · 애프터 · 주간거래일 때만(정규장이면 null). */
    static String gbExtendedSession(String marketPeriod) {
        String state = gbMarketState(marketPeriod);
        return "PRE".equals(state) || "AFTER".equals(state) || "DAYTIME".equals(state) ? state : null;
    }

    /**
     * 국내 현재가(UNT) 응답의 경고 이름표. 같은 종류가 겹쳐도 한 번만 싣는다.
     *
     * <ul>
     *   <li>{@code invt_epmd_yn=Y} → INVESTMENT_ATTENTION(새)</li>
     *   <li>{@code mrkt_alrm_code} 1 · 3 → INVESTMENT_CAUTION · 2 · 4 → INVESTMENT_WARNING · 5 → INVESTMENT_RISK
     *       (3 · 4 의 "위험예고" 는 이름표가 없어 앞말만)</li>
     *   <li>{@code short_over_code} 2 · 3 → SHORT_TERM_OVERHEAT(1 "예고" 는 그리지 않는다)</li>
     *   <li>{@code sltr_yn=Y} → LIQUIDATION_TRADING</li>
     *   <li>{@code Output_2.vi_type_code} 1 · 2 → VI</li>
     * </ul>
     */
    static List<TossStockDto.StockWarning> krWarnings(Map<String, String> quote, Map<String, String> viBlock) {
        List<String> types = new ArrayList<>();
        if (yes(get(quote, "invt_epmd_yn"))) {
            types.add(INVESTMENT_ATTENTION);
        }
        String alarm = get(quote, "mrkt_alrm_code");
        if (alarm != null) {
            switch (alarm) {
                case "1", "3" -> types.add("INVESTMENT_CAUTION");
                case "2", "4" -> types.add("INVESTMENT_WARNING");
                case "5" -> types.add("INVESTMENT_RISK");
                default -> {
                }
            }
        }
        String overheat = get(quote, "short_over_code");
        if ("2".equals(overheat) || "3".equals(overheat)) {
            types.add("SHORT_TERM_OVERHEAT");
        }
        if (yes(get(quote, "sltr_yn"))) {
            types.add("LIQUIDATION_TRADING");
        }
        String vi = get(viBlock, "vi_type_code");
        if ("1".equals(vi) || "2".equals(vi)) {
            types.add("VI");
        }
        return types.stream().distinct()
            .map(t -> new TossStockDto.StockWarning(t, null, null, null))
            .toList();
    }

    private static boolean yes(String v) {
        return "Y".equalsIgnoreCase(v);
    }

    /** 맵에서 값을 꺼낸다 — 없거나 공백이면 null. */
    static String get(Map<String, String> m, String key) {
        return m == null ? null : blankToNull(m.get(key));
    }

    static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /** 가격 · 한도 칸용 — 비었거나 0 이면 null("0" 은 값이 아니라 없다는 뜻으로 온다). */
    static String nonZero(String raw) {
        String v = blankToNull(raw);
        if (v == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(v).signum() == 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
