package com.porest.desk.common.config;

import com.github.gavlyukovskiy.boot.jdbc.decorator.DataSourceDecoratorProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * p6spy SQL 로깅 설정이 <b>라이브러리가 실제로 읽는 자리</b>에 있는지 고정한다.
 *
 * <p><b>왜 이 테스트가 있나</b> — 설정이 {@code spring.datasource.p6spy.*} 에 적혀 있었다.
 * 그 prefix 를 읽는 {@code @ConfigurationProperties} 가 없어서
 * ({@link DataSourceDecoratorProperties} 는 {@code decorator.datasource} 다) 네 줄 전부가
 * 죽은 설정이었고, {@code P6SPY_ENABLED} 를 어떤 값으로 두든 p6spy 는 라이브러리 기본값
 * {@code enableLogging=true} 로 돌았다. 스프링은 모르는 키를 조용히 무시하므로 기동도
 * 테스트도 초록불이었다 — 값이 박힌 완성 SQL 이 dev 로그에 계속 남는 동안.
 *
 * <p>그래서 문자열 비교가 아니라 <b>라이브러리의 프로퍼티 클래스에 실제로 바인딩</b>해서 본다.
 * 라이브러리가 prefix 를 바꾸면 이 테스트가 먼저 깨진다.
 */
class P6SpyLoggingConfigTest {

    /** {@code @ConfigurationProperties} 가 없어 아무도 읽지 않는, 예전에 쓰던 자리. */
    private static final String DEAD_PREFIX = "spring.datasource.p6spy";

    @Test
    @DisplayName("환경변수를 주지 않으면 p6spy 로깅은 꺼진 채로 바인딩된다")
    void disabledByDefault() throws IOException {
        MutablePropertySources sources = load("application.yml");

        DataSourceDecoratorProperties bound = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("decorator.datasource", DataSourceDecoratorProperties.class)
                .orElseThrow(() -> new AssertionError(
                        "decorator.datasource 아래에 p6spy 설정이 없다 — 라이브러리가 읽는 prefix 가 여기다"));

        assertThat(bound.getP6spy().isEnableLogging()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"application.yml", "application-test.yml"})
    @DisplayName("죽은 prefix 로 되돌아가지 않는다 — 스프링이 조용히 무시해 아무도 못 알아챈다")
    void noKeysUnderDeadPrefix(String resource) throws IOException {
        assertThat(keysOf(load(resource)))
                .noneMatch(key -> key.startsWith(DEAD_PREFIX));
    }

    private static MutablePropertySources load(String resource) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load(resource, new ClassPathResource(resource));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        return sources;
    }

    private static List<String> keysOf(MutablePropertySources sources) {
        return sources.stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast)
                .map(EnumerablePropertySource::getPropertyNames)
                .flatMap(Arrays::stream)
                .toList();
    }
}
