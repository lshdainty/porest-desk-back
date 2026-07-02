package com.porest.desk.savingGoal.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.savingGoal.domain.SavingGoal;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SavingGoal QueryDsl 리포 슬라이스 테스트 — 목표 목록 정렬(sortOrder)·소유권·soft-delete 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        SavingGoalQueryDslRepository.class})
@ActiveProfiles("test")
class SavingGoalRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private SavingGoalRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private SavingGoal persistGoal(User user, String title, int sortOrder) {
        return em.persist(SavingGoal.createSavingGoal(user, title, null, 1_000_000L, "KRW",
                null, null, null, null, sortOrder));
    }

    @Test
    @DisplayName("findByUser — 본인 목표만 sortOrder 오름차순으로 반환, 타인 제외")
    void findByUserOrdered() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistGoal(user, "여행 자금", 2);
        persistGoal(user, "비상금", 0);
        persistGoal(user, "노트북", 1);
        persistGoal(other, "남의 목표", 0);
        em.flush();
        em.clear();

        List<SavingGoal> result = repository.findByUser(user.getRowId());

        assertThat(result).extracting(SavingGoal::getTitle)
                .containsExactly("비상금", "노트북", "여행 자금");
    }

    @Test
    @DisplayName("soft delete 된 목표는 findByUser / findById 모두에서 제외된다")
    void softDeleteExcluded() {
        User user = persistUser("u1");
        SavingGoal live = persistGoal(user, "비상금", 0);
        SavingGoal removed = persistGoal(user, "삭제될 목표", 1);
        em.flush();

        repository.delete(removed); // deleteSavingGoal() → isDeleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findByUser(user.getRowId()))
                .extracting(SavingGoal::getRowId).containsExactly(live.getRowId());
        assertThat(repository.findById(removed.getRowId())).isEmpty();
        assertThat(repository.findById(live.getRowId())).isPresent();
    }
}
