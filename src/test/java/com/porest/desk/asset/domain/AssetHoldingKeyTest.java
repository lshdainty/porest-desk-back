package com.porest.desk.asset.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한 자산 안에서 보유를 유일하게 만드는 키 — <b>DB {@code UNIQUE} 와 정확히 같은 판정</b>이어야 한다.
 *
 * <p>이 규칙이 어긋나면 "코드는 없다고 하는데 DB 는 있다고 하는" 상태가 되어, 멀쩡한 편집·매수가
 * 유일성 위반으로 통째로 실패한다. 그래서 판정 두 개를 여기서 못 박는다 —
 * 대소문자·앞뒤공백을 안 가리는 것(콜레이션 {@code utf8mb4_unicode_ci}), 그리고 연동 여부가
 * 키의 일부라는 것.
 */
class AssetHoldingKeyTest {

    @Test
    @DisplayName("대소문자·앞뒤공백은 같은 값으로 본다")
    void ignoresCaseAndSurroundingSpace() {
        assertThat(AssetHolding.normalizeKey("  aapl ")).isEqualTo("AAPL");
        assertThat(AssetHolding.normalizeKey("AAPL")).isEqualTo("AAPL");
        assertThat(AssetHolding.uniquenessKey(true, " aapl"))
            .isEqualTo(AssetHolding.uniquenessKey(true, "AAPL "));
    }

    @Test
    @DisplayName("비어 있으면 키가 없다 — 키 없는 행은 유일성이 안 걸린다")
    void blankHasNoKey() {
        assertThat(AssetHolding.normalizeKey(null)).isNull();
        assertThat(AssetHolding.normalizeKey("   ")).isNull();
        assertThat(AssetHolding.uniquenessKey(true, "   ")).isNull();
    }

    @Test
    @DisplayName("연동 여부가 키의 일부다 — 골드바와 금광주는 이름이 같아도 다른 보유다")
    void linkedFlagIsPartOfTheKey() {
        assertThat(AssetHolding.uniquenessKey(true, "GOLD"))
            .isNotEqualTo(AssetHolding.uniquenessKey(false, "GOLD"));
    }

    @Test
    @DisplayName("보유 행의 키는 연동이면 종목코드, 아니면 항목명에서 나온다")
    void keyComesFromTheRightColumn() {
        AssetHolding linked = AssetHolding.create(null, null, com.porest.core.type.YNType.Y,
            null, " tsla ", null, "무시되는 이름", null, 0L, 0);
        AssetHolding manual = AssetHolding.create(null, null, com.porest.core.type.YNType.N,
            null, "무시되는 코드", null, " 금괴 ", null, 0L, 0);

        assertThat(linked.uniquenessKey()).isEqualTo(AssetHolding.uniquenessKey(true, "TSLA"));
        assertThat(manual.uniquenessKey()).isEqualTo(AssetHolding.uniquenessKey(false, "금괴"));
    }
}
