package com.porest.desk.expense.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.Expense;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expense QueryDsl 리포 슬라이스 테스트 — H2 에서 카테고리 존재여부·롤업 합산·필터 조회 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ExpenseQueryDslRepository.class})
@ActiveProfiles("test")
class ExpenseRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExpenseRepository repository;

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 30);

    private User persistUser() {
        return em.persist(User.createUser(null, "u1", "테스터", "u1@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name, ExpenseCategory parent) {
        return em.persist(ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, parent));
    }

    private Expense persistExpense(User user, ExpenseCategory cat, long amount) {
        return em.persist(Expense.createExpense(user, cat, null, ExpenseType.EXPENSE, amount,
                "거래", LocalDateTime.of(2026, 6, 15, 12, 0), "가게", "CARD"));
    }

    @Test
    @DisplayName("existsByCategory — 거래가 있으면 true, 없으면 false")
    void existsByCategory() {
        User user = persistUser();
        ExpenseCategory used = persistCategory(user, "식비", null);
        ExpenseCategory empty = persistCategory(user, "교통", null);
        persistExpense(user, used, 10_000L);
        em.flush();
        em.clear();

        assertThat(repository.existsByCategory(used.getRowId())).isTrue();
        assertThat(repository.existsByCategory(empty.getRowId())).isFalse();
    }

    @Test
    @DisplayName("sumAmountByCategoryRollup — 부모 + 자식 카테고리 지출을 합산한다")
    void sumRollup() {
        User user = persistUser();
        ExpenseCategory parent = persistCategory(user, "건강", null);
        ExpenseCategory child = persistCategory(user, "의료비", parent);
        persistExpense(user, parent, 5_000L);   // 부모 직접 지출
        persistExpense(user, child, 10_000L);   // 자식 지출 → 부모로 roll-up
        em.flush();
        em.clear();

        long sum = repository.sumAmountByCategoryRollup(
                user.getRowId(), parent.getRowId(), ExpenseType.EXPENSE, START, END);

        assertThat(sum).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("findByUser — categoryRowId 필터로 해당 카테고리 거래만 반환")
    void findByUserCategoryFilter() {
        User user = persistUser();
        ExpenseCategory food = persistCategory(user, "식비", null);
        ExpenseCategory transport = persistCategory(user, "교통", null);
        persistExpense(user, food, 10_000L);
        persistExpense(user, transport, 3_000L);
        em.flush();
        em.clear();

        List<Expense> result = repository.findByUser(
                user.getRowId(), food.getRowId(), ExpenseType.EXPENSE, START, END);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory().getCategoryName()).isEqualTo("식비");
    }
}
