package com.porest.desk.dataimport.service;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;
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
        SYNONYMS.put(ImportField.MEMO, List.of("내용", "메모", "설명", "적요", "거래처", "비고", "contents", "note", "details", "description", "가맹점"));
    }

    /** 편한가계부·머니매니저 위치 고정 순서: A~G. */
    private static final ImportField[] EASYBUDGET_ORDER = {
        ImportField.DATE, ImportField.ASSET, ImportField.CATEGORY, ImportField.SUBCATEGORY,
        ImportField.MEMO, ImportField.AMOUNT, ImportField.TYPE
    };

    // ── 매핑 제안 ────────────────────────────────────────────

    public static Map<ImportField, Integer> suggest(ImportSource source, List<String> headers) {
        if (source == ImportSource.EASYBUDGET) {
            Map<ImportField, Integer> m = new EnumMap<>(ImportField.class);
            for (int i = 0; i < EASYBUDGET_ORDER.length && i < headers.size(); i++) {
                m.put(EASYBUDGET_ORDER[i], i);
            }
            return m;
        }
        return byName(headers);
    }

    private static Map<ImportField, Integer> byName(List<String> headers) {
        List<String> norm = headers.stream().map(ImportColumnMapper::norm).toList();
        Map<ImportField, Integer> result = new EnumMap<>(ImportField.class);
        Set<Integer> used = new HashSet<>();
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
        return result;
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

        String error = null;
        if (date == null) error = "date";
        else if (amount == null) error = "amount";
        else if (type == null) error = "type";

        return new StandardRow(lineNo, date, type, amount,
            blankToNull(category), blankToNull(subcategory), blankToNull(asset), blankToNull(memo),
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
