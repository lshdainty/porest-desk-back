package com.porest.desk.common.config.database;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 엔티티 매핑이 기대하는 테이블·컬럼이 DB 에 실재하는지 기동 시 확인한다. 없으면 기동을 중단시킨다.
 *
 * <p><b>왜 필요한가</b> — 이 레포는 {@code ddl-auto: none} 이라 <b>엔티티와 스키마가 어긋나도 기동이
 * 성공한다.</b> 그리고 첫 쿼리에서 터진다. 같은 사고가 이틀에 두 번 났다.
 * <ul>
 *   <li>dev(2026-08-24) — 앱이 마이그레이션보다 먼저 떴다. 11:58 기동 성공, 14:36 에 누군가 대시보드를
 *       열자 {@code Unknown column 'a1_0.market_code'} 3461건 · HTTP 500 716건. 노출 구간 2시간 37분.
 *       {@code expense} 가 {@code asset} 을 {@code left join} 해서 자산뿐 아니라 가계부·대시보드·
 *       반복거래까지 6개 API 가 동시에 죽었다.</li>
 *   <li>운영(2026-08-24) — 반대 방향. DB 가 먼저 반영돼 옛 코드가 {@code toss_symbol} ·
 *       {@code user_toss_credential} 을 찾다가 에러가 났다.</li>
 * </ul>
 * 두 방향 다 <b>기동은 성공했다.</b> 그게 문제다. 이 검사의 목적은 "기동 성공 = 스키마 일치" 를
 * 성립시키는 것이다.
 *
 * <p><b>왜 {@code hbm2ddl.auto=validate} 가 아닌가</b> — validate 는 컬럼 <b>타입</b>까지 본다.
 * 이 레포의 DDL 은 Hibernate 가 아니라 {@code porest-sql} 에서 손으로 쓰므로 타입 표기가 다를 수밖에
 * 없고, 실제로 지금 dev 스키마에 validate 를 켜면 무해한 차이 두 건으로 기동이 죽는다 —
 * {@code dutch_pay_participant.is_deleted}(매핑 {@code enum('n','y')} ↔ DB {@code char(1)}),
 * {@code stock_master.price_decimals}(매핑 {@code integer} ↔ DB {@code tinyint(4)}).
 * {@code MariaDBDialect.equivalentTypes} 가 둘 다 {@code false} 로 판정한다(ENUM↔VARCHAR 만 통과한다).
 * 오탐이 나면 배포가 통째로 막히므로, 두 사고가 모두 <b>컬럼 부재</b>였다는 점에 맞춰
 * <b>존재 여부만</b> 본다. 덤으로 빠진 것을 첫 건에서 멈추지 않고 전부 모아 한 번에 보고한다.
 *
 * <p><b>커버 범위</b> — 엔티티 매핑({@code @Entity} + 식별자·버전·판별자 컬럼)에서 기대치를 끌어내므로
 * 컬럼을 리네임하면 목록이 저절로 따라온다. {@code @Formula} 와 서브셀렉트 매핑은 컬럼이 아니라
 * 건너뛴다. 컬렉션 전용 테이블({@code @JoinTable} · {@code @ElementCollection})은 보지 않는다 —
 * 지금 이 레포에는 하나도 없고, 못 보는 쪽은 오탐이 아니라 미탐이라 안전한 방향이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.schema-check", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchemaConsistencyCheck {

    private static final String COLUMNS_QUERY =
        "select table_name, column_name from information_schema.columns";

    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;

    @PostConstruct
    void verifySchemaMatchesMapping() {
        Map<String, Set<String>> expected = mappedTables();
        Map<String, Set<String>> actual = actualTables();

        List<String> missingTables = new ArrayList<>();
        List<String> missingColumns = new ArrayList<>();
        int expectedColumns = 0;

        for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
            String table = entry.getKey();
            expectedColumns += entry.getValue().size();
            Set<String> columns = actual.get(table);
            if (columns == null) {
                missingTables.add(table);
                continue;
            }
            for (String column : entry.getValue()) {
                if (!columns.contains(column)) {
                    missingColumns.add(table + "." + column);
                }
            }
        }

        if (!missingTables.isEmpty() || !missingColumns.isEmpty()) {
            List<String> lines = describe(missingTables, missingColumns);
            log.error("""
                스키마 검증 실패 — 엔티티가 기대하는 것이 DB 에 없다.
                  {}
                마이그레이션이 아직 안 돌았거나, 코드가 스키마보다 앞서(또는 뒤처져) 배포됐다.
                이대로 뜨면 기동은 성공하고 첫 쿼리에서 Unknown column 으로 터진다. 기동을 중단한다.""",
                String.join("\n  ", lines));
            throw new IllegalStateException("스키마가 엔티티 매핑과 다르다 — " + String.join("; ", lines));
        }

        log.info("스키마 검증 완료 — 테이블 {}개 / 컬럼 {}개", expected.size(), expectedColumns);
    }

    /** 빠진 것을 한 줄씩. 테이블이 통째로 없으면 그 테이블 컬럼은 낱개로 쏟지 않는다(로그가 못 읽게 된다). */
    private static List<String> describe(List<String> missingTables, List<String> missingColumns) {
        List<String> lines = new ArrayList<>(2);
        if (!missingTables.isEmpty()) {
            lines.add("없는 테이블 %d개: %s".formatted(missingTables.size(), String.join(", ", missingTables)));
        }
        if (!missingColumns.isEmpty()) {
            lines.add("없는 컬럼 %d개: %s".formatted(missingColumns.size(), String.join(", ", missingColumns)));
        }
        return lines;
    }

    /** 엔티티 매핑이 기대하는 테이블 → 컬럼. 이름은 전부 소문자로 맞춘다(H2 는 대문자로 돌려준다). */
    private Map<String, Set<String>> mappedTables() {
        MappingMetamodel metamodel = entityManagerFactory
            .unwrap(SessionFactory.class)
            .unwrap(SessionFactoryImplementor.class)
            .getMappingMetamodel();

        Map<String, Set<String>> expected = new TreeMap<>();
        metamodel.forEachEntityDescriptor(persister -> {
            SelectableConsumer collect = (index, selectable) -> {
                if (selectable.isFormula()) {
                    return;
                }
                String table = identifier(selectable.getContainingTableExpression());
                String column = identifier(selectable.getSelectionExpression());
                if (table == null || column == null) {
                    return;
                }
                expected.computeIfAbsent(table, t -> new TreeSet<>()).add(column);
            };
            persister.forEachSelectable(collect);
            // 식별자·버전·판별자는 forEachSelectable 에 안 들어온다(확인함 — PK row_id 51개가 통째로 빠졌다).
            persister.getIdentifierMapping().forEachSelectable(collect);
            if (persister.getVersionMapping() != null) {
                persister.getVersionMapping().forEachSelectable(collect);
            }
            if (persister.getDiscriminatorMapping() != null) {
                persister.getDiscriminatorMapping().forEachSelectable(collect);
            }
            String table = identifier(persister.getMappedTableDetails().getTableName());
            if (table != null) {
                expected.computeIfAbsent(table, t -> new TreeSet<>());
            }
        });
        return expected;
    }

    /** DB 에 실재하는 테이블 → 컬럼. */
    private Map<String, Set<String>> actualTables() {
        try (Connection connection = dataSource.getConnection()) {
            // 스키마 이름을 정확히 집으면 다른 DB 의 동명 테이블에 속지 않는다. 다만 벤더마다
            // catalog/schema 중 무엇이 information_schema.table_schema 와 맞는지가 달라
            // (MariaDB 는 catalog="desk", H2 는 schema="PUBLIC") 순서대로 시도하고,
            // 전부 빈손이면 필터 없이 읽는다 — 필터가 헛나가서 "전부 없음" 으로 오탐하는 것보다 낫다.
            for (String schema : candidateSchemas(connection)) {
                Map<String, Set<String>> found = readColumns(connection, schema);
                if (!found.isEmpty()) {
                    return found;
                }
            }
            return readColumns(connection, null);
        } catch (SQLException e) {
            throw new IllegalStateException("스키마를 읽지 못해 검증할 수 없다 — 기동을 중단한다", e);
        }
    }

    private static Set<String> candidateSchemas(Connection connection) {
        Set<String> candidates = new LinkedHashSet<>();
        addIfPresent(candidates, catalogOf(connection));
        addIfPresent(candidates, schemaOf(connection));
        return candidates;
    }

    private static void addIfPresent(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value);
        }
    }

    private static String catalogOf(Connection connection) {
        try {
            return connection.getCatalog();
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }

    private static String schemaOf(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Set<String>> readColumns(Connection connection, String schema) throws SQLException {
        String sql = schema == null ? COLUMNS_QUERY : COLUMNS_QUERY + " where table_schema = ?";
        Map<String, Set<String>> actual = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (schema != null) {
                statement.setString(1, schema);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String table = lower(rs.getString(1));
                    String column = lower(rs.getString(2));
                    if (table != null && column != null) {
                        actual.computeIfAbsent(table, t -> new TreeSet<>()).add(column);
                    }
                }
            }
        }
        return actual;
    }

    /**
     * 매핑이 준 이름을 비교 가능한 식별자로 정리한다. 따옴표를 벗기고 소문자로 맞추며,
     * 서브셀렉트({@code ( select ... )}) 처럼 테이블 이름이 아닌 것은 {@code null} 로 걸러 낸다.
     */
    private static String identifier(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 1) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
        }
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(SchemaConsistencyCheck::isIdentifierChar)) {
            return null;
        }
        return lower(trimmed);
    }

    private static boolean isIdentifierChar(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
