package com.porest.desk.calendar.client;

import com.porest.desk.calendar.type.HolidayType;

import java.util.Map;

/**
 * 외부 소스의 공휴일 표기를 desk DB 표기로 맞춘다.
 *
 * <p>특일정보 API 와 월력요항은 "1월1일", "기독탄신일" 같은 관보 표기를 쓰는데, desk 캘린더는
 * "신정", "크리스마스" 로 노출해 왔다. 소스가 바뀌어도 화면 표기가 흔들리지 않도록 한 곳에서 정규화한다.
 */
public final class HolidayNameNormalizer {

    /** 관보/월력요항 표기 → desk 표기. 공백을 제거한 형태를 키로 둬 소스 간 띄어쓰기 차이를 흡수한다. */
    private static final Map<String, String> NAME_MAP = Map.ofEntries(
        Map.entry("1월1일", "신정"),
        Map.entry("3·1절", "삼일절"),
        Map.entry("3ㆍ1절", "삼일절"),
        Map.entry("설날전날", "설날연휴"),
        Map.entry("설날다음날", "설날연휴"),
        Map.entry("추석전날", "추석연휴"),
        Map.entry("추석다음날", "추석연휴"),
        Map.entry("부처님오신날", "석가탄신일"),
        Map.entry("기독탄신일", "크리스마스")
    );

    private static final String SUBSTITUTE_PREFIX = "대체공휴일";

    private HolidayNameNormalizer() {
    }

    /**
     * 외부 표기를 desk 표기로 바꾼다. 매핑에 없는 이름(선거일·임시공휴일 등)은 원문을 그대로 쓴다.
     */
    public static String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return rawName;
        }

        String name = rawName.trim();

        // "대체공휴일(부처님 오신 날)" 처럼 괄호 안에 원 공휴일명이 들어오는 형태는 안쪽만 치환한다.
        if (name.startsWith(SUBSTITUTE_PREFIX + "(") && name.endsWith(")")) {
            String inner = name.substring(SUBSTITUTE_PREFIX.length() + 1, name.length() - 1);
            return SUBSTITUTE_PREFIX + "(" + lookup(inner) + ")";
        }

        return lookup(name);
    }

    /**
     * 공휴일 이름으로 유형을 판정한다.
     *
     * <p>특일정보 API 는 대체공휴일·임시공휴일·선거일을 별도 코드로 구분해 주지 않아 이름으로 가른다.
     */
    public static HolidayType resolveType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return HolidayType.PUBLIC;
        }

        String name = rawName.trim();
        if (name.startsWith(SUBSTITUTE_PREFIX) || name.contains("임시공휴일") || name.contains("선거")) {
            return HolidayType.SUBSTITUTE;
        }
        return HolidayType.PUBLIC;
    }

    private static String lookup(String name) {
        String compact = name.replace(" ", "");
        return NAME_MAP.getOrDefault(compact, name);
    }
}
