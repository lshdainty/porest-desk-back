package com.porest.desk.expense.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * ExpenseBudget QueryDsl 리포 슬라이스 테스트 — H2(create-drop)에서 실제 SQL 로 검증.
 *
 * <p>예산에는 soft-delete 가 없고, 사용자·연월·카테고리 필터와 정렬,
 * 그리고 "전체 예산(category IS NULL)" 브랜치가 핵심 검증 대상이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ExpenseBudgetQueryDslRepository.class})
@ActiveProfiles("test")
class ExpenseBudgetRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExpenseBudgetRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name) {
        return em.persist(ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, null));
    }

    private ExpenseBudget persistBudget(User user, ExpenseCategory category, long amount, int year, int month) {
        return em.persist(ExpenseBudget.createBudget(user, category, amount, year, month));
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        ExpenseBudget budget = ExpenseBudget.createBudget(user, null, 300_000L, 2026, 6);
        repository.save(budget);
        em.flush();
        em.clear();

        Optional<ExpenseBudget> found = repository.findById(budget.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getBudgetAmount()).isEqualTo(300_000L);
        assertThat(found.get().getBudgetYear()).isEqualTo(2026);
        assertThat(found.get().getBudgetMonth()).isEqualTo(6);
    }

    @Test
    @DisplayName("findByUser — 본인 예산만 연·월 내림차순으로 반환하고 타인 예산은 제외한다")
    void findByUserOrderedAndScoped() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistBudget(user, null, 10_000L, 2026, 5);
        persistBudget(user, null, 20_000L, 2026, 7);
        persistBudget(user, null, 30_000L, 2025, 12);
        persistBudget(other, null, 99_000L, 2026, 7); // 타인 → 제외
        em.flush();
        em.clear();

        List<ExpenseBudget> result = repository.findByUser(user.getRowId(), null, null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ExpenseBudget::getBudgetYear, ExpenseBudget::getBudgetMonth)
                .containsExactly(
                        tuple(2026, 7),
                        tuple(2026, 5),
                        tuple(2025, 12));
    }

    @Test
    @DisplayName("findByUser — year·month 옵션 필터로 해당 월만 반환한다")
    void findByUserYearMonthFilter() {
        User user = persistUser("u1");
        persistBudget(user, null, 10_000L, 2026, 6);
        persistBudget(user, null, 20_000L, 2026, 7);
        em.flush();
        em.clear();

        List<ExpenseBudget> result = repository.findByUser(user.getRowId(), 2026, 6);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBudgetAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("findAllByYearAndMonth — 해당 연월의 모든 사용자 예산을 반환하고 다른 월은 제외한다")
    void findAllByYearAndMonth() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistBudget(user, null, 10_000L, 2026, 6);
        persistBudget(other, null, 20_000L, 2026, 6);
        persistBudget(user, null, 30_000L, 2026, 7); // 다른 월 → 제외
        em.flush();
        em.clear();

        List<ExpenseBudget> result = repository.findAllByYearAndMonth(2026, 6);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExpenseBudget::getBudgetAmount)
                .containsExactlyInAnyOrder(10_000L, 20_000L);
    }

    @Test
    @DisplayName("findByUserAndCategory — categoryRowId 지정 시 해당 카테고리, null 이면 전체(카테고리 IS NULL) 예산을 반환한다")
    void findByUserAndCategoryBothBranches() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        persistBudget(user, null, 100_000L, 2026, 6); // 전체 예산 (category IS NULL)
        persistBudget(user, cat, 50_000L, 2026, 6);    // 카테고리 예산
        em.flush();
        em.clear();

        Optional<ExpenseBudget> categoryBudget =
                repository.findByUserAndCategory(user.getRowId(), cat.getRowId(), 2026, 6);
        Optional<ExpenseBudget> overallBudget =
                repository.findByUserAndCategory(user.getRowId(), null, 2026, 6);

        assertThat(categoryBudget).isPresent();
        assertThat(categoryBudget.get().getBudgetAmount()).isEqualTo(50_000L);
        assertThat(categoryBudget.get().getCategory()).isNotNull();

        assertThat(overallBudget).isPresent();
        assertThat(overallBudget.get().getBudgetAmount()).isEqualTo(100_000L);
        assertThat(overallBudget.get().getCategory()).isNull();
    }

    @Test
    @DisplayName("findAllByCategory — 해당 카테고리에 걸린 모든 월의 예산을 반환한다")
    void findAllByCategory() {
        User user = persistUser("u1");
        ExpenseCategory cat = persistCategory(user, "식비");
        ExpenseCategory otherCat = persistCategory(user, "교통");
        persistBudget(user, cat, 10_000L, 2026, 6);
        persistBudget(user, cat, 20_000L, 2026, 7);
        persistBudget(user, otherCat, 30_000L, 2026, 6); // 다른 카테고리 → 제외
        em.flush();
        em.clear();

        List<ExpenseBudget> result = repository.findAllByCategory(cat.getRowId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExpenseBudget::getBudgetAmount)
                .containsExactlyInAnyOrder(10_000L, 20_000L);
    }
}
