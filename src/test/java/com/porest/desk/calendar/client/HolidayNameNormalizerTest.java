package com.porest.desk.calendar.client;

import com.porest.desk.calendar.type.HolidayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 외부 소스 표기 → desk 표기 정규화 검증.
 *
 * <p>소스를 KASI ↔ 폴백으로 오갈 때 같은 공휴일이 다른 이름으로 적재되면 (날짜, 이름) 동기화 키가
 * 어긋나 중복 적재된다. 표기 차이를 여기서 모두 흡수해야 한다.
 */
class HolidayNameNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "1월1일,신정",
            "3·1절,삼일절",
            "3ㆍ1절,삼일절",
            "설날 전날,설날",
            "설날 다음 날,설날",
            "추석 전날,추석",
            "추석 다음 날,추석",
            "부처님 오신 날,석가탄신일",
            "기독탄신일,크리스마스"
    })
    @DisplayName("관보 표기를 desk 표기로 바꾼다")
    void normalizeKnownNames(String raw, String expected) {
        assertThat(HolidayNameNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "부처님오신날,석가탄신일",
            "설날전날,설날"
    })
    @DisplayName("소스 간 띄어쓰기 차이를 흡수한다")
    void normalizeIgnoresSpacing(String raw, String expected) {
        assertThat(HolidayNameNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "대체공휴일(부처님 오신 날),대체공휴일(석가탄신일)",
            "대체공휴일(3ㆍ1절),대체공휴일(삼일절)",
            "대체공휴일(기독탄신일),대체공휴일(크리스마스)",
            "대체공휴일(어린이날),대체공휴일(어린이날)"
    })
    @DisplayName("대체공휴일은 괄호 안쪽 이름만 치환한다")
    void normalizeSubstitute(String raw, String expected) {
        assertThat(HolidayNameNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("KASI 와 폴백이 같은 연휴를 같은 이름으로 정규화한다 — 소스가 오갈 때 추가·삭제 반복 방지")
    void bothSourcesAgreeOnHolidayNames() {
        // 특일정보 API 는 연휴 3일을 모두 같은 dateName 으로 주고, 폴백은 앞뒤날을 구분해 준다.
        // 정규화 결과가 갈리면 (날짜, 이름) 동기화 키가 달라져 소스 전환마다 앞뒤날이 뒤집힌다.
        assertThat(HolidayNameNormalizer.normalize("설날"))          // KASI (연휴 3일 공통)
            .isEqualTo(HolidayNameNormalizer.normalize("설날 전날"))  // 폴백 (전날)
            .isEqualTo(HolidayNameNormalizer.normalize("설날 다음 날"))
            .isEqualTo("설날");

        assertThat(HolidayNameNormalizer.normalize("추석"))
            .isEqualTo(HolidayNameNormalizer.normalize("추석 전날"))
            .isEqualTo(HolidayNameNormalizer.normalize("추석 다음 날"))
            .isEqualTo("추석");
    }

    @ParameterizedTest
    @ValueSource(strings = {"제헌절", "노동절", "전국동시지방선거", "임시공휴일", "어린이날"})
    @DisplayName("매핑에 없는 이름은 원문을 유지한다")
    void normalizeKeepsUnknownNames(String raw) {
        assertThat(HolidayNameNormalizer.normalize(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한다")
    void normalizeTrims() {
        assertThat(HolidayNameNormalizer.normalize("  어린이날  ")).isEqualTo("어린이날");
    }

    @Test
    @DisplayName("null·빈 값은 그대로 돌려준다")
    void normalizeNullSafe() {
        assertThat(HolidayNameNormalizer.normalize(null)).isNull();
        assertThat(HolidayNameNormalizer.normalize("")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"대체공휴일(설날)", "대체공휴일", "임시공휴일", "임시공휴일(국군의 날)",
            "전국동시지방선거", "제21대 국회의원선거"})
    @DisplayName("대체·임시공휴일과 선거일은 SUBSTITUTE 로 판정한다")
    void resolveSubstituteType(String raw) {
        assertThat(HolidayNameNormalizer.resolveType(raw)).isEqualTo(HolidayType.SUBSTITUTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"신정", "설날", "삼일절", "제헌절", "노동절", "크리스마스"})
    @DisplayName("그 외는 PUBLIC 으로 판정한다")
    void resolvePublicType(String raw) {
        assertThat(HolidayNameNormalizer.resolveType(raw)).isEqualTo(HolidayType.PUBLIC);
    }

    @Test
    @DisplayName("null·빈 값은 PUBLIC 으로 판정한다")
    void resolveTypeNullSafe() {
        assertThat(HolidayNameNormalizer.resolveType(null)).isEqualTo(HolidayType.PUBLIC);
        assertThat(HolidayNameNormalizer.resolveType("  ")).isEqualTo(HolidayType.PUBLIC);
    }
}
