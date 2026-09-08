package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Todo QueryDsl 리포 슬라이스 테스트 — H2 에서 소유권·soft-delete 제외,
 * 상태/우선순위/카테고리/기간/프로젝트/타입 조건, 정렬, 통계 집계, 리마인더 조회를 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        TodoQueryDslRepository.class})
@ActiveProfiles("test")
class TodoRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private TodoRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    private Todo persistTask(User user, String title, TodoPriority priority, String category,
                             LocalDate dueDate) {
        return em.persist(Todo.createTodo(user, title, null, priority, category, dueDate, TodoType.TASK));
    }

    private Todo persistNote(User user, String title, boolean pinned) {
        Todo note = Todo.createTodo(user, title, null, TodoPriority.LOW, null, null, TodoType.NOTE);
        Todo saved = em.persist(note);
        if (pinned) saved.togglePin();
        return saved;
    }

    @Test
    @DisplayName("findById — soft-delete 된 할일은 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        Todo active = persistTask(user, "살아있음", TodoPriority.MEDIUM, null, null);
        Todo deleted = persistTask(user, "삭제됨", TodoPriority.MEDIUM, null, null);
        deleted.deleteTodo();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 소유권·soft-delete 제외 후 sortOrder asc, rowId desc 정렬")
    void findAllByUserOwnershipSoftDeleteOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");

        Todo tA = persistTask(user, "A", TodoPriority.MEDIUM, null, null);
        tA.updateSortOrder(1);
        Todo tB = persistTask(user, "B", TodoPriority.MEDIUM, null, null); // sortOrder 0
        Todo tC = persistTask(user, "C", TodoPriority.MEDIUM, null, null); // sortOrder 0, rowId > tB
        Todo tDeleted = persistTask(user, "삭제", TodoPriority.MEDIUM, null, null);
        tDeleted.deleteTodo();
        persistTask(other, "남의할일", TodoPriority.MEDIUM, null, null); // 소유권 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findAllByUser(user.getRowId(), null, null, null, null, null, null);

        assertThat(result).extracting(Todo::getTitle).containsExactly("C", "B", "A");
    }

    @Test
    @DisplayName("findAllByUser — status·priority·category 스칼라 필터")
    void findAllByUserScalarFilters() {
        User user = persistUser("u1");
        Todo highPending = persistTask(user, "높음대기", TodoPriority.HIGH, "일", null);
        Todo lowDone = persistTask(user, "낮음완료", TodoPriority.LOW, "취미", null);
        lowDone.toggleStatus(); // → COMPLETED
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(user.getRowId(), TodoStatus.COMPLETED, null, null, null, null, null))
                .extracting(Todo::getTitle).containsExactly("낮음완료");
        assertThat(repository.findAllByUser(user.getRowId(), null, TodoPriority.HIGH, null, null, null, null))
                .extracting(Todo::getTitle).containsExactly("높음대기");
        assertThat(repository.findAllByUser(user.getRowId(), null, null, "취미", null, null, null))
                .extracting(Todo::getTitle).containsExactly("낮음완료");
    }

    @Test
    @DisplayName("findAllByUser — type 필터(NOTE 만)")
    void findAllByUserTypeFilter() {
        User user = persistUser("u1");
        persistTask(user, "태스크", TodoPriority.MEDIUM, null, null);
        persistNote(user, "노트", false);
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(user.getRowId(), null, null, null, null, null, TodoType.NOTE))
                .extracting(Todo::getTitle).containsExactly("노트");
    }

    @Test
    @DisplayName("findAllByUser — dueDate 기간(start~end) 경계 포함 필터")
    void findAllByUserDueDateRangeFilter() {
        User user = persistUser("u1");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 20);
        persistTask(user, "start경계", TodoPriority.MEDIUM, null, start);
        persistTask(user, "end경계", TodoPriority.MEDIUM, null, end);
        persistTask(user, "범위전", TodoPriority.MEDIUM, null, start.minusDays(1));
        persistTask(user, "범위후", TodoPriority.MEDIUM, null, end.plusDays(1));
        em.flush();
        em.clear();

        List<Todo> result = repository.findAllByUser(user.getRowId(), null, null, null, start, end, null);

        assertThat(result).extracting(Todo::getTitle).containsExactlyInAnyOrder("start경계", "end경계");
    }

    @Test
    @DisplayName("findByUserAndDueDateBetween — 경계 포함 + dueDate asc, sortOrder asc 정렬, 소유권/soft-delete 제외")
    void findByUserAndDueDateBetweenBoundaryAndOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 20);

        persistTask(user, "onStart", TodoPriority.MEDIUM, null, start);
        Todo mid1 = persistTask(user, "mid1", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 15));
        mid1.updateSortOrder(2);
        Todo mid2 = persistTask(user, "mid2", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 15));
        mid2.updateSortOrder(1);
        persistTask(user, "onEnd", TodoPriority.MEDIUM, null, end);
        persistTask(user, "범위전", TodoPriority.MEDIUM, null, start.minusDays(1));
        persistTask(user, "범위후", TodoPriority.MEDIUM, null, end.plusDays(1));
        Todo deleted = persistTask(user, "삭제", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 12));
        deleted.deleteTodo();
        persistTask(other, "남의것", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 12));
        em.flush();
        em.clear();

        List<Todo> result = repository.findByUserAndDueDateBetween(user.getRowId(), start, end);

        assertThat(result).extracting(Todo::getTitle).containsExactly("onStart", "mid2", "mid1", "onEnd");
    }

    @Test
    @DisplayName("countStatsByUser — [total,pending,inProgress,completed,todayDue,overDue,note,pinnedNote] 8개 통계 (소유권·soft-delete)")
    void countStatsByUser() {
        User user = persistUser("owner");
        User other = persistUser("other");
        LocalDate today = LocalDate.of(2026, 7, 2);
        LocalDate yesterday = today.minusDays(1);

        persistTask(user, "t1대기", TodoPriority.MEDIUM, null, null); // PENDING
        Todo t2 = Todo.createTodo(user, "t2진행", null, TodoPriority.MEDIUM, null, null, TodoType.TASK);
        ReflectionTestUtils.setField(t2, "status", TodoStatus.IN_PROGRESS);
        em.persist(t2);
        Todo t3 = persistTask(user, "t3완료", TodoPriority.MEDIUM, null, null);
        t3.toggleStatus(); // COMPLETED
        persistTask(user, "t4오늘마감", TodoPriority.MEDIUM, null, today); // PENDING + todayDue
        persistTask(user, "t5연체", TodoPriority.MEDIUM, null, yesterday); // PENDING + overDue
        Todo t6 = persistTask(user, "t6완료연체", TodoPriority.MEDIUM, null, yesterday);
        t6.toggleStatus(); // COMPLETED → overDue 아님

        persistNote(user, "n1핀노트", true);  // note + pinned
        persistNote(user, "n2노트", false);   // note

        // 통계에서 제외되어야 할 잡음
        Todo del = persistTask(user, "삭제(제외)", TodoPriority.MEDIUM, null, today);
        del.deleteTodo();
        persistTask(other, "남의것(제외)", TodoPriority.MEDIUM, null, today);
        em.flush();
        em.clear();

        long[] stats = repository.countStatsByUser(user.getRowId(), today);

        assertThat(stats[0]).as("totalTask").isEqualTo(6);       // t1~t6
        assertThat(stats[1]).as("pending").isEqualTo(3);         // t1,t4,t5
        assertThat(stats[2]).as("inProgress").isEqualTo(1);      // t2
        assertThat(stats[3]).as("completed").isEqualTo(2);       // t3,t6
        assertThat(stats[4]).as("todayDue").isEqualTo(1);        // t4
        assertThat(stats[5]).as("overDue").isEqualTo(1);         // t5 (t6 는 완료라 제외)
        assertThat(stats[6]).as("noteCount").isEqualTo(2);       // n1,n2
        assertThat(stats[7]).as("pinnedNoteCount").isEqualTo(1); // n1
    }

    @Test
    @DisplayName("countStatsByUser — 데이터가 없으면 모든 통계 0")
    void countStatsByUserEmpty() {
        User user = persistUser("u1");
        em.flush();
        em.clear();

        long[] stats = repository.countStatsByUser(user.getRowId(), LocalDate.of(2026, 7, 2));

        assertThat(stats).containsExactly(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("findDueTodosForReminder — 기간 내 미완료 TASK 만(전 사용자), 완료/NOTE/범위밖/soft-delete 제외")
    void findDueTodosForReminder() {
        User userA = persistUser("a");
        User userB = persistUser("b");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 12);

        persistTask(userA, "a1대기", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11)); // 포함
        Todo a2 = Todo.createTodo(userA, "a2진행-end경계", null, TodoPriority.MEDIUM, null, end, TodoType.TASK);
        ReflectionTestUtils.setField(a2, "status", TodoStatus.IN_PROGRESS);
        em.persist(a2); // 포함 (미완료 + end 경계)
        persistTask(userB, "b1대기-start경계", TodoPriority.MEDIUM, null, start); // 포함 (다른 사용자)

        Todo done = persistTask(userA, "완료", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11));
        done.toggleStatus(); // 제외 (COMPLETED)
        Todo note = Todo.createTodo(userA, "노트", null, TodoPriority.LOW, null,
                LocalDate.of(2026, 6, 11), TodoType.NOTE);
        em.persist(note); // 제외 (NOTE)
        persistTask(userA, "범위후", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 13)); // 제외
        Todo del = persistTask(userA, "삭제", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11));
        del.deleteTodo(); // 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findDueTodosForReminder(start, end);

        assertThat(result).extracting(Todo::getTitle)
                .containsExactlyInAnyOrder("a1대기", "a2진행-end경계", "b1대기-start경계");
    }

    /**
     * QA #79 — 태그를 개명하면 {@code todo.category} 에 남은 옛 이름도 따라 옮긴다.
     * 목록 필터·내보내기가 이 문자열을 쓰므로, 안 옮기면 개명한 태그로는 아무것도 안 걸린다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): WHERE 의 {@code todo.user.rowId.eq(userRowId)} 를 빼면
     * 남의 할 일까지 옮겨 아래 마지막 단언이 깨지고, {@code isDeleted} 조건을 빼면 지운 행까지 옮긴다.
     */
    @Test
    @DisplayName("renameCategory — 그 사용자의 옛 이름만 옮긴다(남의 할일·삭제된 할일·다른 이름 제외)")
    void renameCategoryMovesOnlyOwnActiveRows() {
        User owner = persistUser("owner");
        User other = persistUser("other");
        Todo a = persistTask(owner, "a", TodoPriority.MEDIUM, "업무", null);
        Todo b = persistTask(owner, "b", TodoPriority.MEDIUM, "업무", null);
        Todo keep = persistTask(owner, "keep", TodoPriority.MEDIUM, "개인", null);
        Todo removed = persistTask(owner, "removed", TodoPriority.MEDIUM, "업무", null);
        Todo foreign = persistTask(other, "foreign", TodoPriority.MEDIUM, "업무", null);
        removed.deleteTodo();
        em.flush();
        em.clear();

        long moved = repository.renameCategory(owner.getRowId(), "업무", "회사");
        em.clear();

        assertThat(moved).isEqualTo(2);
        assertThat(em.find(Todo.class, a.getRowId()).getCategory()).isEqualTo("회사");
        assertThat(em.find(Todo.class, b.getRowId()).getCategory()).isEqualTo("회사");
        assertThat(em.find(Todo.class, keep.getRowId()).getCategory()).isEqualTo("개인");
        assertThat(em.find(Todo.class, removed.getRowId()).getCategory()).isEqualTo("업무");
        assertThat(em.find(Todo.class, foreign.getRowId()).getCategory()).isEqualTo("업무");
    }

    /**
     * 태그를 지울 때 그 이름을 쓰던 할 일의 {@code category} 를 비운다(QA #88).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code clearCategory} 의 {@code setNull} 을
     * {@code set(todo.category, category)} 로 바꾸면(= 아무것도 안 지우는 셈) 첫 두 단언이 깨지고,
     * WHERE 의 사용자·삭제 조건을 빼면 남의 할 일·지운 할 일까지 비워 뒤의 단언이 깨진다.
     */
    @Test
    @DisplayName("clearCategory — 그 사용자의 그 이름만 비운다(남의 할일·삭제된 할일·다른 이름 제외)")
    void clearCategoryClearsOnlyOwnActiveRows() {
        User owner = persistUser("owner2");
        User other = persistUser("other2");
        Todo a = persistTask(owner, "a", TodoPriority.MEDIUM, "업무", null);
        Todo b = persistTask(owner, "b", TodoPriority.MEDIUM, "업무", null);
        Todo keep = persistTask(owner, "keep", TodoPriority.MEDIUM, "개인", null);
        Todo removed = persistTask(owner, "removed", TodoPriority.MEDIUM, "업무", null);
        Todo foreign = persistTask(other, "foreign", TodoPriority.MEDIUM, "업무", null);
        removed.deleteTodo();
        em.flush();
        em.clear();

        long cleared = repository.clearCategory(owner.getRowId(), "업무");
        em.clear();

        assertThat(cleared).isEqualTo(2);
        assertThat(em.find(Todo.class, a.getRowId()).getCategory()).isNull();
        assertThat(em.find(Todo.class, b.getRowId()).getCategory()).isNull();
        assertThat(em.find(Todo.class, keep.getRowId()).getCategory()).isEqualTo("개인");
        assertThat(em.find(Todo.class, removed.getRowId()).getCategory()).isEqualTo("업무");
        assertThat(em.find(Todo.class, foreign.getRowId()).getCategory()).isEqualTo("업무");
    }
}
