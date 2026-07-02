package com.porest.desk.user.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User QueryDsl 리포 슬라이스 테스트 — userId 조회·soft-delete 제외 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        UserQueryDslRepository.class})
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    @Test
    @DisplayName("findByUserId — userId 로 활성 사용자를 조회한다")
    void findByUserId() {
        persistUser("alice");
        persistUser("bob");
        em.flush();
        em.clear();

        Optional<User> found = repository.findByUserId("alice");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findByUserId — 존재하지 않는 userId 는 empty")
    void findByUserIdNotFound() {
        persistUser("alice");
        em.flush();
        em.clear();

        assertThat(repository.findByUserId("nobody")).isEmpty();
    }

    @Test
    @DisplayName("soft delete 된 사용자는 findByUserId / findById 모두에서 제외된다")
    void softDeleteExcluded() {
        User removed = persistUser("gone");
        em.flush();

        removed.deleteUser(); // isDeleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findByUserId("gone")).isEmpty();
        assertThat(repository.findById(removed.getRowId())).isEmpty();
    }
}
