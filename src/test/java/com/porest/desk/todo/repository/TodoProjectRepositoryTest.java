package com.porest.desk.todo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.todo.domain.TodoProject;
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
 * TodoProject QueryDsl 리포 슬라이스 테스트 — 소유권·soft-delete 제외 + sortOrder asc, rowId asc 정렬 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        TodoProjectQueryDslRepository.class})
@ActiveProfiles("test")
class TodoProjectRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TodoProjectRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private TodoProject persistProject(User user, String name) {
        return em.persist(TodoProject.createProject(user, name, null, "#ffffff", "icon"));
    }

    @Test
    @DisplayName("findById — soft-delete 된 프로젝트는 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        TodoProject active = persistProject(user, "살아있음");
        TodoProject deleted = persistProject(user, "삭제됨");
        deleted.deleteProject();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 소유권·soft-delete 제외 후 sortOrder asc, rowId asc 정렬")
    void findAllByUserOwnershipSoftDeleteOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");

        TodoProject p1 = persistProject(user, "P1"); // sortOrder 0
        TodoProject p2 = persistProject(user, "P2"); // sortOrder 0, rowId > p1
        TodoProject p3 = persistProject(user, "P3");
        p3.updateSortOrder(-1); // sortOrder 가 더 작으므로 맨 앞
        TodoProject deleted = persistProject(user, "삭제");
        deleted.deleteProject();
        persistProject(other, "남의프로젝트"); // 소유권 제외
        em.flush();
        em.clear();

        List<TodoProject> result = repository.findAllByUser(user.getRowId());

        // sortOrder asc → P3(-1) 먼저, 그다음 sortOrder 0 그룹은 rowId asc → P1, P2
        assertThat(result).extracting(TodoProject::getProjectName).containsExactly("P3", "P1", "P2");
    }
}
