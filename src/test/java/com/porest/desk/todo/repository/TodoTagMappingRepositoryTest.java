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
 * TodoTagMapping QueryDsl 리포 슬라이스 테스트 — todo 별/다건 매핑 조회(태그 fetch join)와
 * todo 단위 벌크 삭제 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        TodoTagMappingQueryDslRepository.class})
@ActiveProfiles("test")
class TodoTagMappingRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TodoTagMappingRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private Todo persistTodo(User user, String title) {
        return em.persist(Todo.createTodo(user, title, null, TodoPriority.MEDIUM, null, null, null, TodoType.TASK));
    }

    private TodoTag persistTag(User user, String name) {
        return em.persist(TodoTag.createTag(user, name, "#ffffff"));
    }

    private void persistMapping(Todo todo, TodoTag tag) {
        em.persist(TodoTagMapping.create(todo, tag));
    }

    @Test
    @DisplayName("findByTodoId — 해당 todo 의 매핑만 반환하고 태그를 함께 로드한다")
    void findByTodoIdWithTagJoin() {
        User user = persistUser("u1");
        Todo todo1 = persistTodo(user, "todo1");
        Todo todo2 = persistTodo(user, "todo2");
        TodoTag work = persistTag(user, "work");
        TodoTag home = persistTag(user, "home");
        TodoTag other = persistTag(user, "other");
        persistMapping(todo1, work);
        persistMapping(todo1, home);
        persistMapping(todo2, other);
        em.flush();
        em.clear();

        List<TodoTagMapping> result = repository.findByTodoId(todo1.getRowId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(m -> m.getTag().getTagName())
                .containsExactlyInAnyOrder("work", "home");
    }

    @Test
    @DisplayName("findByTodoIds — 여러 todo 의 매핑을 한 번에 반환")
    void findByTodoIds() {
        User user = persistUser("u1");
        Todo todo1 = persistTodo(user, "todo1");
        Todo todo2 = persistTodo(user, "todo2");
        Todo todo3 = persistTodo(user, "todo3");
        persistMapping(todo1, persistTag(user, "t1a"));
        persistMapping(todo1, persistTag(user, "t1b"));
        persistMapping(todo2, persistTag(user, "t2a"));
        persistMapping(todo3, persistTag(user, "t3a")); // 조회 대상 아님
        em.flush();
        em.clear();

        List<TodoTagMapping> result = repository.findByTodoIds(List.of(todo1.getRowId(), todo2.getRowId()));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(m -> m.getTag().getTagName())
                .containsExactlyInAnyOrder("t1a", "t1b", "t2a");
    }

    @Test
    @DisplayName("deleteByTodoId — 지정한 todo 의 매핑만 삭제하고 다른 todo 매핑은 유지")
    void deleteByTodoId() {
        User user = persistUser("u1");
        Todo todo1 = persistTodo(user, "todo1");
        Todo todo2 = persistTodo(user, "todo2");
        persistMapping(todo1, persistTag(user, "a"));
        persistMapping(todo1, persistTag(user, "b"));
        persistMapping(todo2, persistTag(user, "c"));
        em.flush();
        em.clear();

        repository.deleteByTodoId(todo1.getRowId());
        em.clear();

        assertThat(repository.findByTodoId(todo1.getRowId())).isEmpty();
        assertThat(repository.findByTodoId(todo2.getRowId())).hasSize(1);
    }

    /**
     * QA #79 — 태그 삭제는 soft-delete 이고 매핑은 남는다. 거르지 않으면 사용자가 지운 태그가
     * 할 일 응답 {@code tags[]} 에 계속 실린다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): 리포의 {@code tag.isDeleted.eq(YNType.N)} 조건을 빼면
     * 아래가 삭제된 "gone" 까지 받아 깨진다.
     */
    @Test
    @DisplayName("findByTodoId — soft-delete 된 태그의 매핑은 빠진다")
    void findByTodoIdExcludesDeletedTag() {
        User user = persistUser("u1");
        Todo todo = persistTodo(user, "todo1");
        TodoTag alive = persistTag(user, "alive");
        TodoTag gone = persistTag(user, "gone");
        persistMapping(todo, alive);
        persistMapping(todo, gone);
        gone.deleteTag();
        em.flush();
        em.clear();

        assertThat(repository.findByTodoId(todo.getRowId()))
                .extracting(m -> m.getTag().getTagName())
                .containsExactly("alive");
    }

    @Test
    @DisplayName("findByTodoIds — soft-delete 된 태그의 매핑은 배치 조회에서도 빠진다")
    void findByTodoIdsExcludesDeletedTag() {
        User user = persistUser("u1");
        Todo todo1 = persistTodo(user, "todo1");
        Todo todo2 = persistTodo(user, "todo2");
        TodoTag alive = persistTag(user, "alive");
        TodoTag gone = persistTag(user, "gone");
        persistMapping(todo1, alive);
        persistMapping(todo2, gone);
        gone.deleteTag();
        em.flush();
        em.clear();

        assertThat(repository.findByTodoIds(List.of(todo1.getRowId(), todo2.getRowId())))
                .extracting(m -> m.getTag().getTagName())
                .containsExactly("alive");
    }

    @Test
    @DisplayName("deleteByTodoIdAndTagId — 지목한 태그의 매핑만 걷고 나머지는 남긴다")
    void deleteByTodoIdAndTagId() {
        User user = persistUser("u1");
        Todo todo1 = persistTodo(user, "todo1");
        Todo todo2 = persistTodo(user, "todo2");
        TodoTag work = persistTag(user, "work");
        TodoTag home = persistTag(user, "home");
        persistMapping(todo1, work);
        persistMapping(todo1, home);
        persistMapping(todo2, work); // 다른 할 일의 같은 태그는 남아야 한다
        em.flush();
        em.clear();

        repository.deleteByTodoIdAndTagId(todo1.getRowId(), work.getRowId());
        em.clear();

        assertThat(repository.findByTodoId(todo1.getRowId()))
                .extracting(m -> m.getTag().getTagName()).containsExactly("home");
        assertThat(repository.findByTodoId(todo2.getRowId())).hasSize(1);
    }
}
