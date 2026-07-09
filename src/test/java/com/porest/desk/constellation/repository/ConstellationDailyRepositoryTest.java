package com.porest.desk.constellation.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.ConstellationDaily;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일일 관측 리포 — 단건/기간 조회(정렬), 마지막 수집일 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ConstellationDailyQueryDslRepository.class})
@ActiveProfiles("test")
class ConstellationDailyRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ConstellationDailyRepository repository;

    private final LocalDate today = LocalDate.now();

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private Constellation persistConstellation(String key) {
        return em.persist(Constellation.createConstellation(
            key, key + "명", "설명", "blue", 7, "{\"pts\":[],\"edges\":[]}", 1));
    }

    private ConstellationDaily persistDaily(User user, LocalDate date, Constellation c, boolean grown) {
        ConstellationDaily daily = ConstellationDaily.open(user, date, c);
        if (grown) {
            daily.addPoints(7);
            daily.grow();
        }
        return em.persist(daily);
    }

    @Test
    @DisplayName("findByUserAndDate — 본인 해당 일자만 (타 사용자 제외)")
    void findByUserAndDate() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        Constellation c = persistConstellation("dipper");
        persistDaily(user, today, c, false);
        persistDaily(other, today, c, true);
        em.flush();

        assertThat(repository.findByUserAndDate(user.getRowId(), today)).isPresent();
        assertThat(repository.findByUserAndDate(user.getRowId(), today.minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("findByUserAndDateBetween — 기간 필터 + obs_date 오름차순")
    void findBetweenSorted() {
        User user = persistUser("u1");
        Constellation c = persistConstellation("dipper");
        persistDaily(user, today, c, false);
        persistDaily(user, today.minusDays(2), c, true);
        persistDaily(user, today.minusDays(1), c, false);
        persistDaily(user, today.minusDays(10), c, true); // 범위 밖
        em.flush();

        List<ConstellationDaily> rows = repository.findByUserAndDateBetween(
            user.getRowId(), today.minusDays(2), today);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(ConstellationDaily::getObsDate)
            .containsExactly(today.minusDays(2), today.minusDays(1), today);
    }

    @Test
    @DisplayName("findLatestGrownDate — GROWN 인 날 중 최댓값, 없으면 empty")
    void latestGrownDate() {
        User user = persistUser("u1");
        Constellation c = persistConstellation("dipper");
        persistDaily(user, today.minusDays(5), c, true);
        persistDaily(user, today.minusDays(2), c, true);
        persistDaily(user, today.minusDays(1), c, false); // WITHERED
        em.flush();

        assertThat(repository.findLatestGrownDate(user.getRowId()))
            .contains(today.minusDays(2));
        assertThat(repository.findLatestGrownDate(999L)).isEmpty();
    }
}
