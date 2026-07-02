package com.porest.desk.todo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.todo.domain.TodoTag;
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
 * TodoTag QueryDsl 리포 슬라이스 테스트 — 소유권·soft-delete 제외·tagName 정렬,
 * 이름 중복 존재확인(exclude 자기제외), ID 목록 조회 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        TodoTagQueryDslRepository.class})
@ActiveProfiles("test")
class TodoTagRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TodoTagRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private TodoTag persistTag(User user, String name) {
        return em.persist(TodoTag.createTag(user, name, "#ffffff"));
    }

    @Test
    @DisplayName("findById — soft-delete 된 태그는 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        TodoTag active = persistTag(user, "active");
        TodoTag deleted = persistTag(user, "deleted");
        deleted.deleteTag();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 소유권·soft-delete 제외 후 tagName 오름차순 정렬")
    void findAllByUserOwnershipSoftDeleteOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");

        persistTag(user, "banana");
        persistTag(user, "apple");
        persistTag(user, "cherry");
        TodoTag deleted = persistTag(user, "deleted");
        deleted.deleteTag();
        persistTag(other, "남의태그"); // 소유권 제외
        em.flush();
        em.clear();

        List<TodoTag> result = repository.findAllByUser(user.getRowId());

        assertThat(result).extracting(TodoTag::getTagName).containsExactly("apple", "banana", "cherry");
    }

    @Test
    @DisplayName("existsActiveByUserAndName — 활성 동일이름 true, soft-delete/다른사용자/exclude 자기제외 는 false")
    void existsActiveByUserAndName() {
        User user = persistUser("owner");
        User other = persistUser("other");
        TodoTag work = persistTag(user, "work");
        TodoTag gone = persistTag(user, "gone");
        gone.deleteTag(); // soft-delete
        em.flush();
        em.clear();

        // 활성 동일 이름 존재 → true
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "work", null)).isTrue();
        // 없는 이름 → false
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "none", null)).isFalse();
        // soft-delete 된 이름 → false
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "gone", null)).isFalse();
        // excludeRowId 로 자기 자신 제외 → false (다른 동일 이름이 없으므로)
        assertThat(repository.existsActiveByUserAndName(user.getRowId(), "work", work.getRowId())).isFalse();
        // 다른 사용자에게는 존재하지 않음 → false
        assertThat(repository.existsActiveByUserAndName(other.getRowId(), "work", null)).isFalse();
    }

    @Test
    @DisplayName("findAllByIds — 주어진 ID 중 soft-delete 되지 않은 태그만 반환")
    void findAllByIdsExcludesSoftDeleted() {
        User user = persistUser("u1");
        TodoTag t1 = persistTag(user, "t1");
        TodoTag t2 = persistTag(user, "t2");
        TodoTag t3 = persistTag(user, "t3");
        TodoTag deleted = persistTag(user, "deleted");
        deleted.deleteTag();
        em.flush();
        em.clear();

        List<TodoTag> result = repository.findAllByIds(
                List.of(t1.getRowId(), t2.getRowId(), deleted.getRowId()));

        // t3 은 목록에 없어서 제외, deleted 는 soft-delete 로 제외
        assertThat(result).extracting(TodoTag::getTagName).containsExactlyInAnyOrder("t1", "t2");
    }
}
