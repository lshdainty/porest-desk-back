package com.porest.desk.dataimport.service;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 소스(프리셋)별 열 자동매핑 제안 + 매핑 적용(행 → {@link StandardRow}).
 *
 * <p>편한가계부/머니매니저(Realbyte)는 헤더에 "자산"·"금액"이 중복되고 소분류 빈칸이 잦아
 * <b>위치 고정 매핑(A~G)</b>이 안전. 그 외 소스는 헤더 <b>동의어 매칭</b>(정확일치 우선 → 부분포함).
 * 토스류 은행 거래내역은 출금액/입금액 분리 → 유형·금액을 파생한다.
 */
public final class ImportColumnMapper {

    private ImportColumnMapper() {}

    // ── 동의어 사전 (norm 비교) ─────────────────────────────
    private static final Map<ImportField, List<String>> SYNONYMS = new EnumMap<>(ImportField.class);
    static {
        SYNONYMS.put(ImportField.DATE, List.of("날짜", "거래일자", "거래일시", "거래날짜", "일자", "일시", "기간", "date", "거래일"));
        SYNONYMS.put(ImportField.TYPE, List.of("유형", "타입", "수입/지출", "수입지출", "입출금구분", "dc구분", "type", "income/expense", "구분"));
        SYNONYMS.put(ImportField.AMOUNT, List.of("금액", "거래금액", "amount", "krw", "원화"));
        SYNONYMS.put(ImportField.AMOUNT_OUT, List.of("출금액", "출금", "보낸금액", "지출금액", "withdrawal"));
        SYNONYMS.put(ImportField.AMOUNT_IN, List.of("입금액", "입금", "받은금액", "수입금액", "deposit"));
        SYNONYMS.put(ImportField.CATEGORY, List.of("카테고리", "대분류", "분류", "category", "maincategory"));
        SYNONYMS.put(ImportField.SUBCATEGORY, List.of("소분류", "세부분류", "subcategory", "중분류"));
        SYNONYMS.put(ImportField.ASSET, List.of("자산", "결제수단", "계좌", "account", "카드", "지불수단"));
        SYNONYMS.put(ImportField.MEMO, List.of("메모", "설명", "내용", "적요", "비고", "contents", "note", "details", "description"));
        SYNONYMS.put(ImportField.TIME, List.of("시간", "시각", "time"));
        SYNONYMS.put(ImportField.MERCHANT, List.of("거래처", "가맹점", "상호", "merchant", "store", "payee"));
        SYNONYMS.put(ImportField.PAYMENT_METHOD, List.of("결제수단", "지불수단", "paymentmethod"));
    }

    // ── 플랫폼별 열 사양 ─────────────────────────────────────
    // 각 플랫폼이 실제로 내보내는 헤더 이름. 이 이름으로 먼저 정확 매칭하고,
    // 못 채운 필드만 위 동의어 사전으로 보완한다(플랫폼이 양식을 조금 바꿔도 견디도록).
    private static final Map<ImportSource, Map<ImportField, String>> SOURCE_SPEC =
        new EnumMap<>(ImportSource.class);
    static {
        Map<ImportField, String> porest = new EnumMap<>(ImportField.class);
        porest.put(ImportField.DATE, "날짜");
        porest.put(ImportField.TYPE, "유형");
        porest.put(ImportField.CATEGORY, "카테고리");
        porest.put(ImportField.ASSET, "자산");
        porest.put(ImportField.AMOUNT, "금액");
        porest.put(ImportField.MEMO, "설명");
        porest.put(ImportField.MERCHANT, "거래처");
        porest.put(ImportField.PAYMENT_METHOD, "결제수단");
        SOURCE_SPEC.put(ImportSource.POREST, porest);

        // 뱅크샐러드 '가계부 내역' — 날짜와 시간이 다른 열이고, '내용' 이 거래처 자리다.
        Map<ImportField, String> banksalad = new EnumMap<>(ImportField.class);
        banksalad.put(ImportField.DATE, "날짜");
        banksalad.put(ImportField.TIME, "시간");
        banksalad.put(ImportField.TYPE, "타입");
        banksalad.put(ImportField.CATEGORY, "대분류");
        banksalad.put(ImportField.SUBCATEGORY, "소분류");
        banksalad.put(ImportField.MERCHANT, "내용");
        banksalad.put(ImportField.AMOUNT, "금액");
        banksalad.put(ImportField.ASSET, "결제수단");
        banksalad.put(ImportField.MEMO, "메모");
        SOURCE_SPEC.put(ImportSource.BANKSALAD, banksalad);

        // 토스뱅크 거래내역 — 출금액/입금액이 분리돼 유형을 금액에서 파생한다.
        Map<ImportField, String> toss = new EnumMap<>(ImportField.class);
        toss.put(ImportField.DATE, "거래일시");
        toss.put(ImportField.AMOUNT_OUT, "출금액");
        toss.put(ImportField.AMOUNT_IN, "입금액");
        toss.put(ImportField.MEMO, "적요");
        toss.put(ImportField.MERCHANT, "거래점");
        SOURCE_SPEC.put(ImportSource.TOSS, toss);
    }

    /** 편한가계부·머니매니저 위치 고정 순서: A~G. */
    private static final ImportField[] EASYBUDGET_ORDER = {
        ImportField.DATE, ImportField.ASSET, ImportField.CATEGORY, ImportField.SUBCATEGORY,
        ImportField.MEMO, ImportField.AMOUNT, ImportField.TYPE
    };

    // ── 매핑 제안 ────────────────────────────────────────────

    public static Map<ImportField, Integer> suggest(ImportSource source, List<String> headers) {
        // 편한가계부는 헤더에 "자산"·"금액" 이 중복되고 소분류 빈칸이 잦아 이름 매칭이 위험하다 → 위치 고정.
        if (source == ImportSource.EASYBUDGET) {
            Map<ImportField, Integer> m = new EnumMap<>(ImportField.class);
            for (int i = 0; i < EASYBUDGET_ORDER.length && i < headers.size(); i++) {
                m.put(EASYBUDGET_ORDER[i], i);
            }
            return m;
        }

        Map<ImportField, Integer> result = new EnumMap<>(ImportField.class);
        Set<Integer> used = new HashSet<>();
        List<String> norm = headers.stream().map(ImportColumnMapper::norm).toList();

        // 1) 플랫폼 사양대로 정확 매칭 (CUSTOM 은 사양이 없어 건너뛴다)
        Map<ImportField, String> spec = SOURCE_SPEC.get(source);
        if (spec != null) {
            for (Map.Entry<ImportField, String> e : spec.entrySet()) {
                String want = norm(e.getValue());
                for (int i = 0; i < norm.size(); i++) {
                    if (!used.contains(i) && norm.get(i).equals(want)) {
                        result.put(e.getKey(), i);
                        used.add(i);
                        break;
                    }
                }
            }
        }

        // 2) 사양이 못 채운 필드만 동의어로 보완 — 플랫폼이 양식을 조금 바꿔도 견디도록.
        fillByName(result, used, norm);

        // 3) 출금/입금이 잡혔으면 단일 금액 매핑은 버린다 — 둘이 함께 있으면 유형 파생이 어긋난다.
        if (result.containsKey(ImportField.AMOUNT_OUT) || result.containsKey(ImportField.AMOUNT_IN)) {
            result.remove(ImportField.AMOUNT);
        }
        return result;
    }

    private static void fillByName(Map<ImportField, Integer> result, Set<Integer> used, List<String> norm) {
        for (boolean exact : new boolean[]{true, false}) {
            for (Map.Entry<ImportField, List<String>> e : SYNONYMS.entrySet()) {
                if (result.containsKey(e.getKey())) continue;
                for (int i = 0; i < norm.size(); i++) {
                    if (used.contains(i) || norm.get(i).isEmpty()) continue;
                    if (matches(norm.get(i), e.getValue(), exact)) {
                        result.put(e.getKey(), i);
                        used.add(i);
                        break;
                    }
                }
            }
        }
    }

    private static boolean matches(String header, List<String> synonyms, boolean exact) {
        for (String s : synonyms) {
            String ns = norm(s);
            if (exact ? header.equals(ns) : (header.contains(ns) || ns.contains(header))) return true;
        }
        return false;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[\\s/()\\[\\]·.,_-]", "");
    }

    // ── 매핑 적용: 원본 행 → StandardRow ─────────────────────

    public static StandardRow mapRow(Map<ImportField, Integer> mapping, List<String> row, int lineNo) {
        LocalDateTime date = ValueNormalizer.parseDate(get(mapping, ImportField.DATE, row));

        ExpenseType type;
        Long amount;
        if (mapping.containsKey(ImportField.AMOUNT)) {
            amount = ValueNormalizer.parseAmount(get(mapping, ImportField.AMOUNT, row));
            type = mapping.containsKey(ImportField.TYPE)
                ? ValueNormalizer.parseType(get(mapping, ImportField.TYPE, row))
                : ExpenseType.EXPENSE; // 유형 열이 없는 단순 파일은 지출로 간주
        } else {
            // 은행 거래내역(출금/입금 분리) → 유형·금액 파생
            Long out = ValueNormalizer.parseAmount(get(mapping, ImportField.AMOUNT_OUT, row));
            Long in = ValueNormalizer.parseAmount(get(mapping, ImportField.AMOUNT_IN, row));
            if (in != null) { amount = in; type = ExpenseType.INCOME; }
            else if (out != null) { amount = out; type = ExpenseType.EXPENSE; }
            else { amount = null; type = null; }
        }

        String category = get(mapping, ImportField.CATEGORY, row);
        String subcategory = get(mapping, ImportField.SUBCATEGORY, row);
        String asset = get(mapping, ImportField.ASSET, row);
        String memo = get(mapping, ImportField.MEMO, row);
        String merchant = get(mapping, ImportField.MERCHANT, row);
        String paymentMethod = get(mapping, ImportField.PAYMENT_METHOD, row);

        // 날짜와 시각이 다른 열인 소스(뱅크샐러드) — 둘을 합쳐 일시로 만든다.
        // 시각 열이 없거나 못 읽으면 날짜만 쓴다(그날 00:00).
        String rawTime = get(mapping, ImportField.TIME, row);
        if (date != null && !rawTime.isBlank()) {
            LocalTime t = ValueNormalizer.parseTime(rawTime);
            if (t != null) date = date.toLocalDate().atTime(t);
        }

        String error = null;
        if (date == null) error = "date";
        else if (amount == null) error = "amount";
        // 이체는 오류가 아니라 "우리가 다루지 않는 유형" — 건너뜀으로 집계되게 사유를 나눈다.
        else if (type == null) {
            error = ValueNormalizer.isTransfer(get(mapping, ImportField.TYPE, row))
                ? StandardRow.ERROR_TRANSFER
                : "type";
        }

        return new StandardRow(lineNo, date, type, amount,
            blankToNull(category), blankToNull(subcategory), blankToNull(asset), blankToNull(memo),
            blankToNull(merchant), blankToNull(paymentMethod),
            false, error);
    }

    private static String get(Map<ImportField, Integer> mapping, ImportField field, List<String> row) {
        Integer idx = mapping.get(field);
        if (idx == null || idx < 0 || idx >= row.size()) return "";
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
