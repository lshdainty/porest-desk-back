package com.porest.desk.common.util;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.validation.FieldLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이름 정규화 규칙 고정 — 여덟 도메인이 전부 이 한 곳을 지난다.
 *
 * <p>여기가 흔들리면 서비스의 중복 검사와 DB UNIQUE 의 판정이 갈리고,
 * 그 어긋남은 409 가 아니라 500 으로 나온다.
 */
class NameNormalizerTest {

    @Test
    @DisplayName("앞뒤 공백을 떼고 돌려준다 — 선행 공백이 서비스 검사와 DB UNIQUE 를 갈라놓던 자리다")
    void trimsSurroundingWhitespace() {
        assertThat(NameNormalizer.require("  식비 ", FieldLimits.NAME_MAX)).isEqualTo("식비");
        assertThat(NameNormalizer.require("\t업무\n", FieldLimits.NAME_MAX)).isEqualTo("업무");
    }

    @Test
    @DisplayName("가운데 공백과 대소문자는 그대로 둔다 — 저장 값은 사용자가 친 대로다")
    void keepsInnerSpacingAndCase() {
        assertThat(NameNormalizer.require(" Cafe Latte ", FieldLimits.NAME_MAX)).isEqualTo("Cafe Latte");
    }

    @Test
    @DisplayName("빈 이름·공백뿐인 이름·널은 거절한다")
    void rejectsBlankNames() {
        for (String blank : new String[]{null, "", "   ", "\t\n"}) {
            assertThatThrownBy(() -> NameNormalizer.require(blank, FieldLimits.NAME_MAX))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("상한은 trim 뒤 길이로 잰다 — 공백을 채워 컬럼 폭을 넘기지 못한다")
    void measuresLengthAfterTrim() {
        String exact = "가".repeat(FieldLimits.NAME_MAX);
        assertThat(NameNormalizer.require("  " + exact + "  ", FieldLimits.NAME_MAX)).isEqualTo(exact);

        String tooLong = "가".repeat(FieldLimits.NAME_MAX + 1);
        assertThatThrownBy(() -> NameNormalizer.require(tooLong, FieldLimits.NAME_MAX))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("varchar(100) 층은 더 긴 이름을 받는다")
    void wideNamesUseTheWiderLimit() {
        String name = "가".repeat(FieldLimits.WIDE_NAME_MAX);
        assertThat(NameNormalizer.require(name, FieldLimits.WIDE_NAME_MAX)).isEqualTo(name);
        assertThatThrownBy(() -> NameNormalizer.require(name, FieldLimits.NAME_MAX))
                .isInstanceOf(InvalidValueException.class);
    }
}
