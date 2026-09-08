package com.porest.desk.todo.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.domain.TodoTagMapping;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private Todo persistTodo(User user, String title, TodoType type) {
        return em.persist(Todo.createTodo(user, title, null, TodoPriority.MEDIUM, null, null, type));
    }

    private void persistMapping(Todo todo, TodoTag tag) {
        em.persist(TodoTagMapping.create(todo, tag));
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

    @Test
    @DisplayName("findActiveByUserAndName — 활성 동명만, 다른 사용자·soft-delete 는 안 잡힌다")
    void findActiveByUserAndName() {
        User user = persistUser("owner");
        User other = persistUser("other");
        TodoTag work = persistTag(user, "업무");
        TodoTag gone = persistTag(user, "지운것");
        gone.deleteTag();
        persistTag(other, "업무");
        em.flush();
        em.clear();

        assertThat(repository.findActiveByUserAndName(user.getRowId(), "업무"))
                .get().extracting(TodoTag::getRowId).isEqualTo(work.getRowId());
        assertThat(repository.findActiveByUserAndName(user.getRowId(), "지운것")).isEmpty();
        assertThat(repository.findActiveByUserAndName(user.getRowId(), "없는이름")).isEmpty();
    }

    /**
     * QA #79 — 사용 수의 <b>모수</b>를 여기서 못 박는다.
     *
     * <p>세는 것은 "이 태그가 붙은 살아 있는 할 일 전부" 다 — <b>서브태스크도 NOTE 도 함께 센다</b>.
     * 목록 조회({@code findAllByUser})가 최상위만 보는 것과 일부러 다르다: 이 숫자가 나가는
     * 자리는 삭제 확인창의 "이 태그를 쓰는 할 일 N건은 태그 없음으로 남아요" 이고, 서브태스크에
     * 붙은 태그도 똑같이 사라지기 때문이다. 빼면 말한 적 없는 행에서 태그가 사라진다.
     *
     * <p>소유권 축은 <b>할 일 주인</b>이다. 태그 주인으로 걸면 (소유권 검사가 없던 시절에 생긴)
     * 남의 할 일 매핑이 내 태그 사용 수에 섞인다.
     */
    @Test
    @DisplayName("countTodosByTag — 매핑 기준 집계, 축은 할 일 주인, NOTE 포함·삭제 제외")
    void countTodosByTag() {
        User owner = persistUser("owner");
        User other = persistUser("other");
        TodoTag work = persistTag(owner, "업무");
        TodoTag idle = persistTag(owner, "안쓰는것");

        Todo root = persistTodo(owner, "할일1", TodoType.TASK);
        Todo second = persistTodo(owner, "할일2", TodoType.TASK);
        Todo note = persistTodo(owner, "노트", TodoType.NOTE);
        Todo removed = persistTodo(owner, "지운할일", TodoType.TASK);
        Todo foreign = persistTodo(other, "남의할일", TodoType.TASK);
        persistMapping(root, work);
        persistMapping(second, work);
        persistMapping(note, work);
        persistMapping(removed, work);
        persistMapping(foreign, work); // 남의 할 일 — 내 집계에 섞이면 안 된다
        removed.deleteTodo();
        em.flush();
        em.clear();

        Map<Long, Long> counts = repository.countTodosByTag(owner.getRowId());

        assertThat(counts).containsEntry(work.getRowId(), 3L);
        assertThat(counts).doesNotContainKey(idle.getRowId()); // 안 쓰는 태그는 키 자체가 없다
        assertThat(repository.countTodosByTag(other.getRowId()))
                .containsEntry(work.getRowId(), 1L);
    }

    // ── QA #102 — 처음 쓰는 이름의 매핑을 잇는 방법 ──────────────────────────
    // findOrCreateByName 은 새 트랜잭션에서 커밋한다. 부르는 트랜잭션은 그 앞에서 스냅샷을
    // 잡았으므로 MariaDB 기본 격리수준(REPEATABLE READ)에서 그 행을 다시 읽지 못한다.
    // H2 는 격리 수준이 달라 그 상황 자체를 재현하지 못하므로, 여기서는 "우리가 고른 방법"
    // (참조로 잇는다 · 프록시에서 필드를 읽지 않는다)을 붙들어 둔다. 메모 태그의 미러다.

    /**
     * 되돌려 보는 법(네거티브 컨트롤): {@code TodoTagQueryDslRepository.getReference} 를
     * {@code entityManager.find(TodoTag.class, rowId)} 로 바꾸면 아래 첫 단언(초기화되지 않은
     * 참조)이 깨진다 — 그 순간 조회가 나가고, 운영에서는 그 조회가 빈손으로 돌아온다.
     */
    @Test
    @DisplayName("getReference — 조회 없이 참조만 잡고, 그 참조로 남긴 매핑은 그대로 저장된다")
    void getReferenceLinksMappingWithoutSelect() {
        User user = persistUser("u1");
        TodoTag tag = persistTag(user, "업무");
        Todo todo = persistTodo(user, "기획서", TodoType.TASK);
        Long userRowId = user.getRowId();
        Long tagRowId = tag.getRowId();
        Long todoRowId = todo.getRowId();
        em.flush();
        em.clear();

        TodoTag ref = repository.getReference(tagRowId);
        assertThat(Hibernate.isInitialized(ref)).isFalse();

        em.persist(TodoTagMapping.create(em.find(Todo.class, todoRowId), ref));
        em.flush();
        em.clear();

        assertThat(repository.countTodosByTag(userRowId)).containsEntry(tagRowId, 1L);
    }

    /**
     * ★ 이 참조에서 <b>필드를 읽으면</b> 그때 조회가 나간다 — 아이디 게터도 예외가 아니다
     * (필드 접근 매핑이라 프록시가 가로채지 못한다). 운영에서 그 조회는 스냅샷에 없는 행을
     * 찾으므로 빈손으로 돌아오고 {@code EntityNotFoundException} 이 된다. 그래서 이름·색은
     * {@code findOrCreateByName} 이 함께 돌려주는 {@code TagRef} 에서 읽는다.
     */
    @Test
    @DisplayName("getReference — 없는 행의 참조는 필드를 읽는 순간 터진다(이름을 프록시에서 읽으면 안 되는 이유)")
    void readingProxyFieldTriggersSelect() {
        TodoTag ghost = repository.getReference(999_999L);

        assertThat(Hibernate.isInitialized(ghost)).isFalse();
        assertThatThrownBy(ghost::getTagName)
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(ghost::getRowId)
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
