package com.porest.desk.user.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SupportedCurrency#PATTERN} 이 {@link SupportedCurrency#values()} 와 어긋나지 않게 잡는다.
 *
 * <p>애노테이션 속성은 컴파일 상수여야 해서 {@code @Pattern(regexp = ...)} 에 {@code values()} 를
 * 넣을 수 없다. 그래서 목록이 문자열로 한 번 더 적혀 있고, 통화를 하나 늘리면 두 자리를 다
 * 고쳐야 한다 — 한쪽만 고치면 <b>화면에는 뜨는데 저장은 400</b> 이 되거나 그 반대가 된다.
 *
 * <p>되돌려 보는 법(네거티브 컨트롤): enum 에 상수를 하나 더 넣고 {@code PATTERN} 을 그대로
 * 두면 아래가 깨진다.
 */
class SupportedCurrencyTest {

    @Test
    @DisplayName("PATTERN 은 values() 목록과 같다")
    void patternMatchesValues() {
        assertThat(SupportedCurrency.PATTERN).isEqualTo(SupportedCurrency.joined());
    }

    @Test
    @DisplayName("DEFAULT 는 목록 안의 값이다")
    void defaultIsSupported() {
        assertThat(SupportedCurrency.contains(SupportedCurrency.DEFAULT)).isTrue();
    }

    @Test
    @DisplayName("contains — 목록 밖·null 은 false")
    void containsRejectsUnknown() {
        assertThat(SupportedCurrency.contains("XBT")).isFalse();
        assertThat(SupportedCurrency.contains("krw")).isFalse();
        assertThat(SupportedCurrency.contains(null)).isFalse();
    }
}
