package com.porest.desk;

import com.porest.desk.security.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 애플리케이션이 실제로 뜨는지 — 전체 컨텍스트 기동.
 *
 * <p>슬라이스 테스트만 있으면 빈 배선 문제를 못 잡는다. 실제로 놓쳤다: 필터에 서비스를 직접
 * 주입했더니 톰캣이 필터 빈을 모으는 시점(JPA 초기화보다 앞선다)에
 * {@code UserRepository → QueryDslConfig → EntityManager} 가 끌려 들어와
 * "No qualifying bean of type EntityManager" 로 기동이 깨졌다. 단위 테스트는 필터를
 * {@code new} 로 만들고, @WebMvcTest 는 이 필터를 아예 제외해서 둘 다 통과했다.
 *
 * <p>기동 순서가 걸린 문제라 컨텍스트를 통째로 올려야만 드러난다.
 */
// RANDOM_PORT 여야 한다 — MOCK 은 실제 톰캣을 띄우지 않아 필터 빈을 모으는 단계 자체가 없다.
// 그 단계에서 나는 문제라, MOCK 으로는 깨진 코드도 그냥 통과한다(확인함).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationContextLoadTest {

    @Autowired private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("전체 컨텍스트가 뜬다 — 필터가 JPA 빈을 너무 이르게 끌어오지 않는다")
    void contextLoads() {
        assertThat(jwtAuthenticationFilter).isNotNull();
    }
}
