package com.porest.desk.common.config.database;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기동 시 스키마 검증 — "검사를 짰다" 가 아니라 "없으면 진짜 기동이 죽는다" 를 확인한다.
 *
 * <p>H2 는 {@code create-drop} 이라 스키마가 항상 매핑과 같다. 그래서 어긋난 상황을
 * <b>실제로 만든다</b> — dev 를 2시간 37분 동안 죽였던 그 컬럼({@code asset.market_code})을
 * 떨어뜨리고, 테이블 하나를 통째로 지운다. 그 상태에서 컨텍스트를 띄워 기동이 실패하는지,
 * 실패 메시지가 어느 테이블의 어느 컬럼인지 말하는지 본다.
 *
 * <p>DDL 로 H2 를 망가뜨리므로 {@code @DirtiesContext} 로 스키마를 새로 만들게 한다
 * (finally 복구도 하지만, 복구가 실패해도 다른 테스트에 새지 않게 하는 안전망이다).
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaConsistencyCheckTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private DataSource dataSource;
    @Autowired(required = false) private SchemaConsistencyCheck registeredBean;

    @Test
    @DisplayName("기본값으로 켜져 있고, 매핑과 맞는 스키마에서는 통과한다")
    void passesOnMatchingSchema() {
        assertThat(registeredBean).as("app.schema-check.enabled 기본값은 켬이어야 한다").isNotNull();
        assertThatCode(() -> newCheck().verifySchemaMatchesMapping()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("컬럼이 빠지면 기동이 죽는다 — 어느 테이블의 어느 컬럼인지 말한다")
    void failsWhenColumnMissing() {
        withoutAssetMarketCode(() ->
            assertThatThrownBy(() -> newCheck().verifySchemaMatchesMapping())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("없는 컬럼")
                .hasMessageContaining("asset.market_code"));
    }

    @Test
    @DisplayName("컬럼이 빠지면 컨텍스트 자체가 못 뜬다")
    void contextFailsWhenColumnMissing() {
        withoutAssetMarketCode(() -> contextRunner().run(context -> assertThat(context)
            .hasFailed()
            .getFailure()
            .rootCause()
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("asset.market_code")));
    }

    @Test
    @DisplayName("테이블이 통째로 없으면 테이블 이름을 말한다 — 그 테이블 컬럼을 낱개로 쏟지 않는다")
    void failsWhenTableMissing() {
        // drop 후 create 로 되돌리면 원본 DDL 을 손으로 베껴야 한다. rename 이면 그럴 일이 없다.
        execute("alter table todo_tag_mapping rename to todo_tag_mapping_gone");
        try {
            assertThatThrownBy(() -> newCheck().verifySchemaMatchesMapping())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("없는 테이블")
                .hasMessageContaining("todo_tag_mapping")
                .hasMessageNotContaining("todo_tag_mapping.");
        } finally {
            execute("alter table todo_tag_mapping_gone rename to todo_tag_mapping");
        }
    }

    @Test
    @DisplayName("app.schema-check.enabled=false 면 검사 자체가 등록되지 않는다")
    void disabledByProperty() {
        withoutAssetMarketCode(() -> contextRunner()
            .withPropertyValues("app.schema-check.enabled=false")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(SchemaConsistencyCheck.class)));
    }

    /** 이미 떠 있는 컨텍스트의 EMF·DataSource 만 빌려 검사 빈 하나짜리 컨텍스트를 띄운다. */
    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withBean(EntityManagerFactory.class, () -> entityManagerFactory, BORROWED)
            .withBean(DataSource.class, () -> dataSource, BORROWED)
            .withUserConfiguration(SchemaConsistencyCheck.class);
    }

    /**
     * 빌려 온 빈이니 이 컨텍스트가 닫힐 때 건드리지 말라고 표시한다. 둘 다 {@code AutoCloseable} 이라
     * 그냥 두면 스프링이 close() 를 추론해 부른다 — 실제로 겪었다. 자식 컨텍스트가 닫히며 바깥
     * EMF 가 닫히고 {@code create-drop} 이 돌아 H2 테이블 51개가 통째로 사라졌다.
     */
    private static final BeanDefinitionCustomizer BORROWED = definition -> definition.setDestroyMethodName("");

    private SchemaConsistencyCheck newCheck() {
        return new SchemaConsistencyCheck(entityManagerFactory, dataSource);
    }

    /** dev 를 죽였던 그 컬럼을 실제로 떨어뜨린다. 끝나면 되돌린다. */
    private void withoutAssetMarketCode(Runnable body) {
        execute("alter table asset drop column market_code");
        try {
            body.run();
        } finally {
            execute("alter table asset add column market_code varchar(10)");
        }
    }

    private void execute(String ddl) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("테스트 DDL 실패: " + ddl, e);
        }
    }
}
