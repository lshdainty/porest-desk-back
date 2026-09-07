package com.porest.desk.common.patch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Patch} 는 PUT 본문의 세 가지 상태를 옮기는 값이다 — 여기서 그 셋의 경계를 못 박는다.
 *
 * <p>특히 {@link Patch#from(Optional)} 의 <b>{@code null} 참조 = 키 없음</b> 규약이 중요하다.
 * Jackson 이 그렇게 채우고({@code AbsentAwareOptionalModule}) 이 규약을 아는 자리는 여기 하나다.
 */
class PatchTest {

    @Test
    @DisplayName("from — null 참조는 '키 없음', Optional.empty 는 '지워라', 값은 '바꿔라'")
    void fromMapsThreeStates() {
        assertThat(Patch.from(null)).isEqualTo(Patch.absent());
        assertThat(Patch.from(Optional.empty())).isEqualTo(Patch.set(null));
        assertThat(Patch.from(Optional.of("v"))).isEqualTo(Patch.set("v"));
    }

    @Test
    @DisplayName("orKeep — 안 왔으면 지금 값, 왔으면 실린 값(null 이면 null)")
    void orKeep() {
        assertThat(Patch.<String>absent().orKeep("지금")).isEqualTo("지금");
        assertThat(Patch.set("새것").orKeep("지금")).isEqualTo("새것");
        assertThat(Patch.<String>set(null).orKeep("지금")).isNull();
    }

    @Test
    @DisplayName("map — 안 온 값은 변환도 안 한다(없는 아이디를 조회하지 않는다)")
    void mapSkipsAbsent() {
        AtomicInteger calls = new AtomicInteger();

        Patch<String> mapped = Patch.<Long>absent().map(id -> {
            calls.incrementAndGet();
            return "조회함";
        });

        assertThat(mapped).isEqualTo(Patch.absent());
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("map — 명시적 null 은 변환 없이 null 그대로 '지워라' 로 남는다")
    void mapKeepsExplicitNull() {
        AtomicInteger calls = new AtomicInteger();

        Patch<String> mapped = Patch.<Long>set(null).map(id -> {
            calls.incrementAndGet();
            return "조회함";
        });

        assertThat(mapped).isEqualTo(Patch.set(null));
        assertThat(mapped.orKeep("지금")).isNull();
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("map — 실린 값만 변환한다")
    void mapConvertsPresentValue() {
        assertThat(Patch.set(7L).map(id -> "자산#" + id)).isEqualTo(Patch.set("자산#7"));
    }

    @Test
    @DisplayName("absent 는 한 벌만 쓴다 — 타입이 달라도 같은 인스턴스다")
    void absentIsShared() {
        assertThat(Patch.<String>absent()).isSameAs(Patch.<Long>absent());
        assertThat(Patch.absent().present()).isFalse();
        assertThat(Patch.absent().value()).isNull();
    }
}
