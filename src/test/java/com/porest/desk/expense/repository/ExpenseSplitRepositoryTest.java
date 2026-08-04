package com.porest.desk.expense.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.type.ExpenseType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExpenseSplit QueryDsl 리포 슬라이스 테스트 — H2(create-drop)에서 실제 SQL 로 검증.
 *
 * <p>분할 항목의 soft-delete(isDeleted=N) 필터, expense 별 정렬, 벌크 soft-delete,
 * 그리고 사용자 월별 카테고리·타입 합계 집계(JPQL)를 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ExpenseSplitQueryDslRepository.class})
@ActiveProfiles("test")
class ExpenseSplitRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExpenseSplitRepository repository;

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 30);

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name) {
        return em.persist(ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, null));
    }

    private Expense persistExpense(User user, ExpenseCategory cat, ExpenseType type, long amount, LocalDateTime when) {
        return em.persist(Expense.createExpense(user, cat, null, type, amount, "거래", when, "가게", "CARD", null, null));
    }

    private ExpenseSplit persistSplit(Expense expense, ExpenseCategory cat, long amount, String label, int sortOrder) {
        return em.persist(ExpenseSplit.create(expense, cat, amount, label, sortOrder));
    }

    @Test
    @DisplayName("findById — soft-delete 된 분할은 조회되지 않는다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        Expense expense = persistExpense(user, cat, ExpenseType.EXPENSE, 10_000L, LocalDateTime.of(2026, 6, 10, 9, 0));
        ExpenseSplit active = persistSplit(expense, cat, 3_000L, "활성", 0);
        ExpenseSplit deleted = persistSplit(expense, cat, 2_000L, "삭제", 1);
        deleted.deleteSplit();
        em.flush();
        em.clear();

        assertThat(repository.findById(active.getRowId())).isPresent();
        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findByExpense — 해당 expense 의 활성 분할만 sortOrder 오름차순으로 반환한다")
    void findByExpenseOrderedAndActiveOnly() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        Expense expense = persistExpense(user, cat, ExpenseType.EXPENSE, 10_000L, LocalDateTime.of(2026, 6, 10, 9, 0));
        Expense other = persistExpense(user, cat, ExpenseType.EXPENSE, 5_000L, LocalDateTime.of(2026, 6, 11, 9, 0));
        persistSplit(expense, cat, 1_000L, "c", 2);
        persistSplit(expense, cat, 1_000L, "a", 0);
        persistSplit(expense, cat, 1_000L, "b", 1);
        ExpenseSplit deleted = persistSplit(expense, cat, 1_000L, "x", 0);
        deleted.deleteSplit();                 // soft-delete → 제외
        persistSplit(other, cat, 1_000L, "다른거래", 0); // 다른 expense → 제외
        em.flush();
        em.clear();

        List<ExpenseSplit> result = repository.findByExpense(expense.getRowId());

        assertThat(result).extracting(ExpenseSplit::getLabel)
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("findByExpenseIds — 빈 목록이면 빈 결과, 값이 있으면 expense·sortOrder 순으로 반환한다")
    void findByExpenseIdsGroupingAndEmpty() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        Expense e1 = persistExpense(user, cat, ExpenseType.EXPENSE, 10_000L, LocalDateTime.of(2026, 6, 10, 9, 0));
        Expense e2 = persistExpense(user, cat, ExpenseType.EXPENSE, 5_000L, LocalDateTime.of(2026, 6, 11, 9, 0));
        persistSplit(e1, cat, 1_000L, "e1-so1", 1);
        persistSplit(e1, cat, 1_000L, "e1-so0", 0);
        persistSplit(e2, cat, 1_000L, "e2-so0", 0);
        em.flush();
        em.clear();

        assertThat(repository.findByExpenseIds(List.of())).isEmpty();

        List<ExpenseSplit> result = repository.findByExpenseIds(List.of(e1.getRowId(), e2.getRowId()));

        assertThat(result).extracting(ExpenseSplit::getLabel)
                .containsExactly("e1-so0", "e1-so1", "e2-so0");
    }

    @Test
    @DisplayName("existsActiveByCategory — 활성 분할이 있으면 true, 없거나 삭제만 있으면 false")
    void existsActiveByCategory() {
        User user = persistUser("u1");
        ExpenseCategory used = persistCategory(user, "식비");
        ExpenseCategory onlyDeleted = persistCategory(user, "교통");
        ExpenseCategory unused = persistCategory(user, "문화");
        Expense expense = persistExpense(user, used, ExpenseType.EXPENSE, 10_000L, LocalDateTime.of(2026, 6, 10, 9, 0));
        persistSplit(expense, used, 3_000L, "활성", 0);
        ExpenseSplit deleted = persistSplit(expense, onlyDeleted, 2_000L, "삭제", 1);
        deleted.deleteSplit();
        em.flush();
        em.clear();

        assertThat(repository.existsActiveByCategory(used.getRowId())).isTrue();
        assertThat(repository.existsActiveByCategory(onlyDeleted.getRowId())).isFalse();
        assertThat(repository.existsActiveByCategory(unused.getRowId())).isFalse();
    }

    @Test
    @DisplayName("deleteByExpense — 대상 expense 의 분할만 soft-delete 하고 다른 expense 분할은 유지한다")
    void deleteByExpenseSoftDeletesTargetOnly() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        Expense target = persistExpense(user, cat, ExpenseType.EXPENSE, 10_000L, LocalDateTime.of(2026, 6, 10, 9, 0));
        Expense keep = persistExpense(user, cat, ExpenseType.EXPENSE, 5_000L, LocalDateTime.of(2026, 6, 11, 9, 0));
        persistSplit(target, cat, 3_000L, "t1", 0);
        persistSplit(target, cat, 2_000L, "t2", 1);
        persistSplit(keep, cat, 1_000L, "k1", 0);
        em.flush();
        em.clear();

        repository.deleteByExpense(target.getRowId());

        assertThat(repository.findByExpense(target.getRowId())).isEmpty();
        assertThat(repository.findByExpense(keep.getRowId()))
                .extracting(ExpenseSplit::getLabel)
                .containsExactly("k1");
    }

    @Test
    @DisplayName("sumMonthlyByUserGroupedByCategoryAndType — 카테고리·타입별로 합산하고 삭제·범위밖·타인 항목은 제외한다")
    void sumMonthlyGroupedWithFilters() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        ExpenseCategory c1 = persistCategory(user, "식비");
        ExpenseCategory c2 = persistCategory(user, "교통");
        ExpenseCategory otherCat = persistCategory(other, "남의카테고리");

        // 정상 지출: (c1,EXPENSE) 3000+2000=5000, (c2,EXPENSE) 1000
        Expense e1 = persistExpense(user, c1, ExpenseType.EXPENSE, 6_000L, LocalDateTime.of(2026, 6, 15, 12, 0));
        persistSplit(e1, c1, 3_000L, "s1", 0);
        persistSplit(e1, c1, 2_000L, "s2", 1);
        persistSplit(e1, c2, 1_000L, "s3", 2);
        ExpenseSplit deletedSplit = persistSplit(e1, c2, 9_999L, "삭제분할", 3);
        deletedSplit.deleteSplit(); // split soft-delete → 제외

        // 정상 수입: (c1,INCOME) 500
        Expense e2 = persistExpense(user, c1, ExpenseType.INCOME, 500L, LocalDateTime.of(2026, 6, 20, 9, 0));
        persistSplit(e2, c1, 500L, "s4", 0);

        // soft-delete 된 expense → 제외
        Expense e3 = persistExpense(user, c1, ExpenseType.EXPENSE, 7_777L, LocalDateTime.of(2026, 6, 18, 9, 0));
        persistSplit(e3, c1, 7_777L, "삭제거래분할", 0);
        e3.deleteExpense();

        // 기간 밖(6/1 이전) → 제외
        Expense e4 = persistExpense(user, c1, ExpenseType.EXPENSE, 8_888L, LocalDateTime.of(2026, 5, 31, 12, 0));
        persistSplit(e4, c1, 8_888L, "범위밖분할", 0);

        // 타인 거래 → 제외
        Expense e5 = persistExpense(other, otherCat, ExpenseType.EXPENSE, 6_666L, LocalDateTime.of(2026, 6, 15, 12, 0));
        persistSplit(e5, otherCat, 6_666L, "타인분할", 0);

        em.flush();
        em.clear();

        List<Object[]> rows = repository.sumMonthlyByUserGroupedByCategoryAndType(user.getRowId(), START, END);

        Map<String, Long> sums = new HashMap<>();
        for (Object[] row : rows) {
            String key = ((Number) row[0]).longValue() + "|" + String.valueOf(row[1]);
            sums.put(key, ((Number) row[2]).longValue());
        }

        assertThat(sums).hasSize(3);
        assertThat(sums).containsEntry(c1.getRowId() + "|EXPENSE", 5_000L);
        assertThat(sums).containsEntry(c2.getRowId() + "|EXPENSE", 1_000L);
        assertThat(sums).containsEntry(c1.getRowId() + "|INCOME", 500L);
    }
}
