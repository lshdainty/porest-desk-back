package com.porest.desk.constellation.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.ConstellationCollection;
import com.porest.desk.constellation.domain.ConstellationProfile;
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
 * 수집 스냅샷 + 프로필 리포 — 별자리별 그룹 통계(횟수/마지막 수집일), 누적 카운트, 프로필 단건 조회 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ConstellationCollectionQueryDslRepository.class, ConstellationProfileQueryDslRepository.class})
@ActiveProfiles("test")
class ConstellationCollectionRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ConstellationCollectionRepository repository;
    @Autowired private ConstellationProfileRepository profileRepository;

    private final LocalDate today = LocalDate.now();

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private Constellation persistConstellation(String key, int sortOrder) {
        return em.persist(Constellation.createConstellation(
            key, key + "명", key + "-en", "설명", "desc", "blue", 7, "{\"pts\":[],\"edges\":[]}", sortOrder));
    }

    @Test
    @DisplayName("findStatsByUser — 별자리별 횟수 + 마지막 수집일 (본인 것만)")
    void statsGroupByConstellation() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        Constellation dipper = persistConstellation("dipper", 1);
        Constellation cass = persistConstellation("cass", 2);
        em.persist(ConstellationCollection.collect(user, dipper, today.minusDays(3)));
        em.persist(ConstellationCollection.collect(user, dipper, today.minusDays(1)));
        em.persist(ConstellationCollection.collect(user, cass, today.minusDays(2)));
        em.persist(ConstellationCollection.collect(other, dipper, today));
        em.flush();

        List<ConstellationCollectionRepository.CollectionStat> stats = repository.findStatsByUser(user.getRowId());

        assertThat(stats).hasSize(2);
        ConstellationCollectionRepository.CollectionStat dipperStat = stats.stream()
            .filter(stat -> stat.constellationRowId().equals(dipper.getRowId())).findFirst().orElseThrow();
        assertThat(dipperStat.count()).isEqualTo(2);
        assertThat(dipperStat.lastCollectedDate()).isEqualTo(today.minusDays(1));
        assertThat(repository.countByUser(user.getRowId())).isEqualTo(3);
        assertThat(repository.countByUser(other.getRowId())).isEqualTo(1);
    }

    @Test
    @DisplayName("프로필 — findByUser 단건, 없으면 empty")
    void profileFindByUser() {
        User user = persistUser("u1");
        em.persist(ConstellationProfile.createProfile(user));
        em.flush();

        assertThat(profileRepository.findByUser(user.getRowId())).isPresent();
        assertThat(profileRepository.findByUser(999L)).isEmpty();
    }
}
