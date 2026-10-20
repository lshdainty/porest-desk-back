package io.groo.damda.common.search;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.core.types.dsl.StringPath;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 카탈로그 검색어 규칙 — NFC · 소문자 · 공백 지움. 웹 · 앱 Searchable List 의 거르기 규칙과 같다.
 * "모아 카드" 로 쳐도 "모아카드" 가 나온다.
 */
public final class SearchKeyword {

    private SearchKeyword() {
    }

    /** 검색어를 NFC · 소문자 · 공백 제거로 맞춘다. */
    public static String normalize(String keyword) {
        if (keyword == null) {
            return "";
        }
        return Normalizer.normalize(keyword, Normalizer.Form.NFC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "");
    }

    /** 컬럼 쪽도 소문자 · 공백 제거로 맞춘다. */
    public static StringExpression normalized(StringPath path) {
        return Expressions.stringTemplate("replace(lower({0}), ' ', '')", path);
    }

    /** 정규화한 컬럼이 정규화한 검색어를 포함하는가. */
    public static BooleanExpression contains(StringPath path, String normalizedKeyword) {
        return normalized(path).contains(normalizedKeyword);
    }

    public static BooleanExpression startsWith(StringPath path, String normalizedKeyword) {
        return normalized(path).startsWith(normalizedKeyword);
    }

    public static BooleanExpression equalsTo(StringPath path, String normalizedKeyword) {
        return normalized(path).eq(normalizedKeyword);
    }
}
