package com.porest.desk.constellation.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.constellation.domain.TodoStarlight;
import com.porest.desk.constellation.type.StarlightSourceType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별빛 원장 리포 — 평생 1회(회수 포함 존재 검사), 유효 원장 조회, 메모 일 한도 카운트, 일자 합계 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        TodoStarlightQueryDslRepository.class})
@ActiveProfiles("test")
class TodoStarlightRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TodoStarlightRepository repository;

    private final LocalDate today = LocalDate.now();

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private TodoStarlight persistEarn(User user, StarlightSourceType type, long sourceRowId, int points, LocalDate date) {
        return em.persist(TodoStarlight.earn(user, type, sourceRowId, points, date));
    }

    @Test
    @DisplayName("findBySourceIncludingRevoked — 회수(soft delete)돼도 원장 행을 돌려준다 (평생 1회 근거)")
    void findIncludesRevoked() {
        User user = persistUser("u1");
        TodoStarlight ledger = persistEarn(user, StarlightSourceType.TODO, 7L, 3, today);
        em.flush();

        assertThat(repository.findBySourceIncludingRevoked(StarlightSourceType.TODO, 7L))
            .hasValueSatisfying(found -> assertThat(found.isRevoked()).isFalse());

        ledger.revoke();
        em.flush();

        assertThat(repository.findBySourceIncludingRevoked(StarlightSourceType.TODO, 7L))
            .hasValueSatisfying(found -> assertThat(found.isRevoked()).isTrue());
        assertThat(repository.findBySourceIncludingRevoked(StarlightSourceType.TODO, 999L)).isEmpty();
        assertThat(repository.findBySourceIncludingRevoked(StarlightSourceType.MEMO, 7L)).isEmpty();
    }

    @Test
    @DisplayName("findActiveBySource — 회수분 제외한 유효 원장만")
    void findActiveExcludesRevoked() {
        User user = persistUser("u1");
        TodoStarlight ledger = persistEarn(user, StarlightSourceType.TODO, 7L, 3, today);
        em.flush();

        assertThat(repository.findActiveBySource(StarlightSourceType.TODO, 7L)).isPresent();

        ledger.revoke();
        em.flush();

        assertThat(repository.findActiveBySource(StarlightSourceType.TODO, 7L)).isEmpty();
    }

    @Test
    @DisplayName("countActiveMemoEarns — 오늘 유효 메모 적립만 카운트 (회수·타일·타소스 제외)")
    void countActiveMemoEarnsToday() {
        User user = persistUser("u1");
        persistEarn(user, StarlightSourceType.MEMO, 1L, 1, today);
        TodoStarlight revoked = persistEarn(user, StarlightSourceType.MEMO, 2L, 1, today);
        revoked.revoke();
        persistEarn(user, StarlightSourceType.MEMO, 3L, 1, today.minusDays(1)); // 타일
        persistEarn(user, StarlightSourceType.TODO, 4L, 3, today);              // 타소스
        em.flush();

        assertThat(repository.countActiveMemoEarns(user.getRowId(), today)).isEqualTo(1);
    }

    @Test
    @DisplayName("sumActivePointsByDate — 소스 타입별 유효 합계 (회수 제외)")
    void sumActivePointsByDate() {
        User user = persistUser("u1");
        persistEarn(user, StarlightSourceType.TODO, 1L, 3, today);
        persistEarn(user, StarlightSourceType.TODO, 2L, 2, today);
        persistEarn(user, StarlightSourceType.MEMO, 3L, 1, today);
        TodoStarlight revoked = persistEarn(user, StarlightSourceType.TODO, 4L, 3, today);
        revoked.revoke();
        persistEarn(user, StarlightSourceType.TODO, 5L, 1, today.minusDays(1)); // 타일
        em.flush();

        Map<StarlightSourceType, Integer> sums = repository.sumActivePointsByDate(user.getRowId(), today);

        assertThat(sums.get(StarlightSourceType.TODO)).isEqualTo(5);
        assertThat(sums.get(StarlightSourceType.MEMO)).isEqualTo(1);
    }
}
