package com.porest.desk.expense.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecurringTransaction QueryDsl 리포 슬라이스 테스트 — H2(create-drop)에서 실제 SQL 로 검증.
 *
 * <p>soft-delete(isDeleted=N) 필터, nextExecutionDate 오름차순 정렬, 카테고리 참조 여부,
 * 그리고 findDueTransactions 의 복합 실행 조건(활성·기한·최대횟수 가드)을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        RecurringTransactionQueryDslRepository.class})
@ActiveProfiles("test")
class RecurringTransactionRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecurringTransactionRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name) {
        return em.persist(ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, null));
    }

    private RecurringTransaction newRecurring(User user, ExpenseCategory cat, String desc,
                                              LocalDate nextExec, LocalDate endDate, Integer maxOccurrences) {
        return RecurringTransaction.createRecurring(
                user, cat, null, null,
                ExpenseType.EXPENSE, 10_000L, desc, "가게", "TRANSFER",
                RecurringFrequency.MONTHLY, 1, null, 1,
                null,
                LocalDate.of(2026, 1, 1), endDate, maxOccurrences,
                nextExec, true, true);
    }

    @Test
    @DisplayName("findByUser — 본인 반복거래만 nextExecutionDate 오름차순으로 반환하고 타인 것은 제외한다")
    void findByUserOrderedAndScoped() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        em.persist(newRecurring(user, null, "7월", LocalDate.of(2026, 7, 10), null, null));
        em.persist(newRecurring(user, null, "6월", LocalDate.of(2026, 6, 15), null, null));
        em.persist(newRecurring(user, null, "8월", LocalDate.of(2026, 8, 1), null, null));
        em.persist(newRecurring(other, null, "타인", LocalDate.of(2026, 6, 1), null, null));
        em.flush();
        em.clear();

        List<RecurringTransaction> result = repository.findByUser(user.getRowId());

        assertThat(result).extracting(RecurringTransaction::getDescription)
                .containsExactly("6월", "7월", "8월");
    }

    @Test
    @DisplayName("soft delete 후에는 findById·findByUser 에서 제외된다")
    void softDeleteExcluded() {
        User user = persistUser("u1");
        RecurringTransaction keep = em.persist(newRecurring(user, null, "유지", LocalDate.of(2026, 6, 10), null, null));
        RecurringTransaction removed = em.persist(newRecurring(user, null, "삭제", LocalDate.of(2026, 6, 11), null, null));
        em.flush();

        repository.delete(removed); // deleteRecurring() → isDeleted=Y
        em.flush();
        em.clear();

        assertThat(repository.findById(removed.getRowId())).isEmpty();
        assertThat(repository.findByUser(user.getRowId()))
                .extracting(RecurringTransaction::getDescription)
                .containsExactly("유지");
        assertThat(repository.findById(keep.getRowId())).isPresent();
    }

    @Test
    @DisplayName("existsByCategory — 활성 반복거래가 참조하면 true, 없거나 삭제만 있으면 false")
    void existsByCategory() {
        User user = persistUser("u1");
        ExpenseCategory used = persistCategory(user, "구독");
        ExpenseCategory onlyDeleted = persistCategory(user, "통신");
        ExpenseCategory unused = persistCategory(user, "보험");
        em.persist(newRecurring(user, used, "넷플릭스", LocalDate.of(2026, 6, 10), null, null));
        RecurringTransaction deleted =
                em.persist(newRecurring(user, onlyDeleted, "옛통신", LocalDate.of(2026, 6, 10), null, null));
        deleted.deleteRecurring();
        em.flush();
        em.clear();

        assertThat(repository.existsByCategory(used.getRowId())).isTrue();
        assertThat(repository.existsByCategory(onlyDeleted.getRowId())).isFalse();
        assertThat(repository.existsByCategory(unused.getRowId())).isFalse();
    }

    @Test
    @DisplayName("findDueTransactions — 활성·미삭제·실행일도래·기한내·최대횟수 미달 조건을 모두 만족한 건만 반환한다")
    void findDueTransactionsGuards() {
        User user = persistUser("u1");
        LocalDate date = LocalDate.of(2026, 6, 30);

        // 포함: 실행일 도래(<=date), 활성, 기한/최대횟수 없음
        em.persist(newRecurring(user, null, "due", date, null, null));
        em.persist(newRecurring(user, null, "dueEarlier", LocalDate.of(2026, 6, 1), null, null));

        // 제외: 실행일 미도래(nextExecutionDate > date)
        em.persist(newRecurring(user, null, "future", LocalDate.of(2026, 7, 1), null, null));

        // 제외: 비활성(isActive=N)
        RecurringTransaction inactive =
                em.persist(newRecurring(user, null, "inactive", LocalDate.of(2026, 6, 20), null, null));
        inactive.toggleActive();

        // 제외: soft-delete
        RecurringTransaction deleted =
                em.persist(newRecurring(user, null, "deleted", LocalDate.of(2026, 6, 20), null, null));
        deleted.deleteRecurring();

        // 제외: 기한 지남(endDate < date)
        em.persist(newRecurring(user, null, "ended", LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 29), null));
        // 포함: 기한 경계(endDate == date)
        em.persist(newRecurring(user, null, "endsToday", LocalDate.of(2026, 6, 20), date, null));

        // 제외: 최대횟수 도달(executedCount >= maxOccurrences) — 활성으로 되돌려 최대횟수 가드만 격리 검증
        RecurringTransaction maxReached =
                em.persist(newRecurring(user, null, "maxReached", LocalDate.of(2026, 6, 20), null, 2));
        maxReached.markExecuted(LocalDateTime.now(), LocalDate.of(2026, 6, 20)); // count 1
        maxReached.markExecuted(LocalDateTime.now(), LocalDate.of(2026, 6, 20)); // count 2 → isActive=N
        maxReached.toggleActive();                                              // 다시 활성화(count 2 유지)

        // 포함: 최대횟수 미달
        em.persist(newRecurring(user, null, "maxNotReached", LocalDate.of(2026, 6, 20), null, 5));

        em.flush();
        em.clear();

        List<RecurringTransaction> result = repository.findDueTransactions(date);

        assertThat(result).extracting(RecurringTransaction::getDescription)
                .containsExactlyInAnyOrder("due", "dueEarlier", "endsToday", "maxNotReached");
    }
}
