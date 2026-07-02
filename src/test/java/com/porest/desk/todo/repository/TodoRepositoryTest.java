package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoProject;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Todo QueryDsl 리포 슬라이스 테스트 — H2 에서 소유권·soft-delete 제외·parent-null(최상위) 필터,
 * 상태/우선순위/카테고리/기간/프로젝트/타입 조건, 정렬, 서브태스크 집계, 통계 집계, 리마인더 조회를 검증.
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

    private TodoProject persistProject(User user, String name) {
        return em.persist(TodoProject.createProject(user, name, null, "#ffffff", "icon"));
    }

    private Todo persistTask(User user, String title, TodoPriority priority, String category,
                             LocalDate dueDate, TodoProject project, Todo parent) {
        return em.persist(Todo.createTodo(user, title, null, priority, category, dueDate, project, parent, TodoType.TASK));
    }

    private Todo persistNote(User user, String title, boolean pinned) {
        Todo note = Todo.createTodo(user, title, null, TodoPriority.LOW, null, null, null, null, TodoType.NOTE);
        Todo saved = em.persist(note);
        if (pinned) saved.togglePin();
        return saved;
    }

    @Test
    @DisplayName("findById — soft-delete 된 할일은 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        Todo active = persistTask(user, "살아있음", TodoPriority.MEDIUM, null, null, null, null);
        Todo deleted = persistTask(user, "삭제됨", TodoPriority.MEDIUM, null, null, null, null);
        deleted.deleteTodo();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByUser — 소유권·soft-delete·서브태스크(parent!=null) 제외 후 sortOrder asc, rowId desc 정렬")
    void findAllByUserOwnershipParentNullSoftDeleteOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");

        Todo tA = persistTask(user, "A", TodoPriority.MEDIUM, null, null, null, null);
        tA.updateSortOrder(1);
        Todo tB = persistTask(user, "B", TodoPriority.MEDIUM, null, null, null, null); // sortOrder 0
        Todo tC = persistTask(user, "C", TodoPriority.MEDIUM, null, null, null, null); // sortOrder 0, rowId > tB
        Todo tDeleted = persistTask(user, "삭제", TodoPriority.MEDIUM, null, null, null, null);
        tDeleted.deleteTodo();
        persistTask(user, "서브태스크", TodoPriority.MEDIUM, null, null, null, tB); // parent != null → 제외
        persistTask(other, "남의할일", TodoPriority.MEDIUM, null, null, null, null); // 소유권 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findAllByUser(user.getRowId(), null, null, null, null, null, null, null);

        assertThat(result).extracting(Todo::getTitle).containsExactly("C", "B", "A");
    }

    @Test
    @DisplayName("findAllByUser — status·priority·category 스칼라 필터")
    void findAllByUserScalarFilters() {
        User user = persistUser("u1");
        Todo highPending = persistTask(user, "높음대기", TodoPriority.HIGH, "일", null, null, null);
        Todo lowDone = persistTask(user, "낮음완료", TodoPriority.LOW, "취미", null, null, null);
        lowDone.toggleStatus(); // → COMPLETED
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(user.getRowId(), TodoStatus.COMPLETED, null, null, null, null, null, null))
                .extracting(Todo::getTitle).containsExactly("낮음완료");
        assertThat(repository.findAllByUser(user.getRowId(), null, TodoPriority.HIGH, null, null, null, null, null))
                .extracting(Todo::getTitle).containsExactly("높음대기");
        assertThat(repository.findAllByUser(user.getRowId(), null, null, "취미", null, null, null, null))
                .extracting(Todo::getTitle).containsExactly("낮음완료");
    }

    @Test
    @DisplayName("findAllByUser — type 필터(NOTE 만)")
    void findAllByUserTypeFilter() {
        User user = persistUser("u1");
        persistTask(user, "태스크", TodoPriority.MEDIUM, null, null, null, null);
        persistNote(user, "노트", false);
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(user.getRowId(), null, null, null, null, null, null, TodoType.NOTE))
                .extracting(Todo::getTitle).containsExactly("노트");
    }

    @Test
    @DisplayName("findAllByUser — projectRowId 필터로 해당 프로젝트 할일만")
    void findAllByUserProjectFilter() {
        User user = persistUser("u1");
        TodoProject p1 = persistProject(user, "프로젝트1");
        TodoProject p2 = persistProject(user, "프로젝트2");
        persistTask(user, "p1할일", TodoPriority.MEDIUM, null, null, p1, null);
        persistTask(user, "p2할일", TodoPriority.MEDIUM, null, null, p2, null);
        em.flush();
        em.clear();

        assertThat(repository.findAllByUser(user.getRowId(), null, null, null, null, null, p1.getRowId(), null))
                .extracting(Todo::getTitle).containsExactly("p1할일");
    }

    @Test
    @DisplayName("findAllByUser — dueDate 기간(start~end) 경계 포함 필터")
    void findAllByUserDueDateRangeFilter() {
        User user = persistUser("u1");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 20);
        persistTask(user, "start경계", TodoPriority.MEDIUM, null, start, null, null);
        persistTask(user, "end경계", TodoPriority.MEDIUM, null, end, null, null);
        persistTask(user, "범위전", TodoPriority.MEDIUM, null, start.minusDays(1), null, null);
        persistTask(user, "범위후", TodoPriority.MEDIUM, null, end.plusDays(1), null, null);
        em.flush();
        em.clear();

        List<Todo> result = repository.findAllByUser(user.getRowId(), null, null, null, start, end, null, null);

        assertThat(result).extracting(Todo::getTitle).containsExactlyInAnyOrder("start경계", "end경계");
    }

    @Test
    @DisplayName("findByUserAndDueDateBetween — 경계 포함 + dueDate asc, sortOrder asc 정렬, 소유권/soft-delete 제외")
    void findByUserAndDueDateBetweenBoundaryAndOrdering() {
        User user = persistUser("owner");
        User other = persistUser("other");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 20);

        persistTask(user, "onStart", TodoPriority.MEDIUM, null, start, null, null);
        Todo mid1 = persistTask(user, "mid1", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 15), null, null);
        mid1.updateSortOrder(2);
        Todo mid2 = persistTask(user, "mid2", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 15), null, null);
        mid2.updateSortOrder(1);
        persistTask(user, "onEnd", TodoPriority.MEDIUM, null, end, null, null);
        persistTask(user, "범위전", TodoPriority.MEDIUM, null, start.minusDays(1), null, null);
        persistTask(user, "범위후", TodoPriority.MEDIUM, null, end.plusDays(1), null, null);
        Todo deleted = persistTask(user, "삭제", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 12), null, null);
        deleted.deleteTodo();
        persistTask(other, "남의것", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 12), null, null);
        em.flush();
        em.clear();

        List<Todo> result = repository.findByUserAndDueDateBetween(user.getRowId(), start, end);

        assertThat(result).extracting(Todo::getTitle).containsExactly("onStart", "mid2", "mid1", "onEnd");
    }

    @Test
    @DisplayName("findSubtasks — 부모 매칭 + soft-delete 제외 + sortOrder asc, rowId asc 정렬")
    void findSubtasksParentMatchSoftDeleteOrdering() {
        User user = persistUser("u1");
        Todo parent = persistTask(user, "부모", TodoPriority.MEDIUM, null, null, null, null);
        Todo otherParent = persistTask(user, "다른부모", TodoPriority.MEDIUM, null, null, null, null);

        Todo s1 = persistTask(user, "s1", TodoPriority.MEDIUM, null, null, null, parent);
        s1.updateSortOrder(1);
        persistTask(user, "s2", TodoPriority.MEDIUM, null, null, null, parent); // sortOrder 0
        persistTask(user, "s3", TodoPriority.MEDIUM, null, null, null, parent); // sortOrder 0, rowId > s2
        Todo sDel = persistTask(user, "삭제서브", TodoPriority.MEDIUM, null, null, null, parent);
        sDel.deleteTodo();
        persistTask(user, "남의부모서브", TodoPriority.MEDIUM, null, null, null, otherParent); // 다른 부모 → 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findSubtasks(parent.getRowId());

        assertThat(result).extracting(Todo::getTitle).containsExactly("s2", "s3", "s1");
    }

    @Test
    @DisplayName("findSubtaskCountsByParentIds — 부모별 [total, completed] 집계, soft-delete 제외, 빈 리스트는 빈 맵")
    void findSubtaskCountsByParentIds() {
        User user = persistUser("u1");
        Todo p1 = persistTask(user, "부모1", TodoPriority.MEDIUM, null, null, null, null);
        Todo p2 = persistTask(user, "부모2", TodoPriority.MEDIUM, null, null, null, null);

        persistTask(user, "p1-s1", TodoPriority.MEDIUM, null, null, null, p1);
        persistTask(user, "p1-s2", TodoPriority.MEDIUM, null, null, null, p1);
        Todo p1done = persistTask(user, "p1-s3완료", TodoPriority.MEDIUM, null, null, null, p1);
        p1done.toggleStatus(); // COMPLETED
        Todo p1del = persistTask(user, "p1-s4삭제", TodoPriority.MEDIUM, null, null, null, p1);
        p1del.deleteTodo(); // 카운트 제외

        Todo p2done = persistTask(user, "p2-s1완료", TodoPriority.MEDIUM, null, null, null, p2);
        p2done.toggleStatus(); // COMPLETED
        em.flush();
        em.clear();

        Map<Long, int[]> counts = repository.findSubtaskCountsByParentIds(List.of(p1.getRowId(), p2.getRowId()));

        assertThat(counts.get(p1.getRowId())).containsExactly(3, 1); // total 3(삭제 제외), completed 1
        assertThat(counts.get(p2.getRowId())).containsExactly(1, 1);
        assertThat(repository.findSubtaskCountsByParentIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("countStatsByUser — [total,pending,inProgress,completed,todayDue,overDue,note,pinnedNote] 8개 통계 (parent-null·소유권·soft-delete)")
    void countStatsByUser() {
        User user = persistUser("owner");
        User other = persistUser("other");
        LocalDate today = LocalDate.of(2026, 7, 2);
        LocalDate yesterday = today.minusDays(1);

        persistTask(user, "t1대기", TodoPriority.MEDIUM, null, null, null, null); // PENDING
        Todo t2 = Todo.createTodo(user, "t2진행", null, TodoPriority.MEDIUM, null, null, null, null, TodoType.TASK);
        ReflectionTestUtils.setField(t2, "status", TodoStatus.IN_PROGRESS);
        em.persist(t2);
        Todo t3 = persistTask(user, "t3완료", TodoPriority.MEDIUM, null, null, null, null);
        t3.toggleStatus(); // COMPLETED
        persistTask(user, "t4오늘마감", TodoPriority.MEDIUM, null, today, null, null); // PENDING + todayDue
        persistTask(user, "t5연체", TodoPriority.MEDIUM, null, yesterday, null, null); // PENDING + overDue
        Todo t6 = persistTask(user, "t6완료연체", TodoPriority.MEDIUM, null, yesterday, null, null);
        t6.toggleStatus(); // COMPLETED → overDue 아님

        persistNote(user, "n1핀노트", true);  // note + pinned
        persistNote(user, "n2노트", false);   // note

        // 통계에서 제외되어야 할 잡음
        persistTask(user, "서브(제외)", TodoPriority.MEDIUM, null, today, null,
                persistTask(user, "서브부모", TodoPriority.MEDIUM, null, null, null, null));
        Todo del = persistTask(user, "삭제(제외)", TodoPriority.MEDIUM, null, today, null, null);
        del.deleteTodo();
        persistTask(other, "남의것(제외)", TodoPriority.MEDIUM, null, today, null, null);
        em.flush();
        em.clear();

        long[] stats = repository.countStatsByUser(user.getRowId(), today);

        // 부모 태스크 "서브부모"(parent-null, TASK, PENDING) 도 total/pending 에 포함됨
        assertThat(stats[0]).as("totalTask").isEqualTo(7);       // t1~t6 + 서브부모
        assertThat(stats[1]).as("pending").isEqualTo(4);         // t1,t4,t5,서브부모
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
    @DisplayName("findByProject — 프로젝트 매칭 + parent-null·soft-delete 제외 + sortOrder asc, rowId desc 정렬")
    void findByProjectParentNullSoftDeleteOrdering() {
        User user = persistUser("u1");
        TodoProject pr = persistProject(user, "프로젝트");
        TodoProject other = persistProject(user, "다른프로젝트");

        Todo t1 = persistTask(user, "t1", TodoPriority.MEDIUM, null, null, pr, null); // sortOrder 0
        Todo t2 = persistTask(user, "t2", TodoPriority.MEDIUM, null, null, pr, null); // sortOrder 0, rowId > t1
        Todo t3 = persistTask(user, "t3", TodoPriority.MEDIUM, null, null, pr, null);
        t3.updateSortOrder(1);
        persistTask(user, "서브", TodoPriority.MEDIUM, null, null, pr, t1); // parent != null → 제외
        Todo del = persistTask(user, "삭제", TodoPriority.MEDIUM, null, null, pr, null);
        del.deleteTodo();
        persistTask(user, "다른프로젝트할일", TodoPriority.MEDIUM, null, null, other, null); // 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findByProject(pr.getRowId());

        assertThat(result).extracting(Todo::getTitle).containsExactly("t2", "t1", "t3");
    }

    @Test
    @DisplayName("findDueTodosForReminder — 기간 내 미완료 TASK 만(전 사용자), 완료/NOTE/범위밖/soft-delete 제외")
    void findDueTodosForReminder() {
        User userA = persistUser("a");
        User userB = persistUser("b");
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 12);

        persistTask(userA, "a1대기", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11), null, null); // 포함
        Todo a2 = Todo.createTodo(userA, "a2진행-end경계", null, TodoPriority.MEDIUM, null, end, null, null, TodoType.TASK);
        ReflectionTestUtils.setField(a2, "status", TodoStatus.IN_PROGRESS);
        em.persist(a2); // 포함 (미완료 + end 경계)
        persistTask(userB, "b1대기-start경계", TodoPriority.MEDIUM, null, start, null, null); // 포함 (다른 사용자)

        Todo done = persistTask(userA, "완료", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11), null, null);
        done.toggleStatus(); // 제외 (COMPLETED)
        Todo note = Todo.createTodo(userA, "노트", null, TodoPriority.LOW, null,
                LocalDate.of(2026, 6, 11), null, null, TodoType.NOTE);
        em.persist(note); // 제외 (NOTE)
        persistTask(userA, "범위후", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 13), null, null); // 제외
        Todo del = persistTask(userA, "삭제", TodoPriority.MEDIUM, null, LocalDate.of(2026, 6, 11), null, null);
        del.deleteTodo(); // 제외
        em.flush();
        em.clear();

        List<Todo> result = repository.findDueTodosForReminder(start, end);

        assertThat(result).extracting(Todo::getTitle)
                .containsExactlyInAnyOrder("a1대기", "a2진행-end경계", "b1대기-start경계");
    }
}
