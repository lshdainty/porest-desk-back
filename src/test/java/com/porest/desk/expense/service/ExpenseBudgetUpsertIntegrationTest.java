package com.porest.desk.expense.service;

import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA 2026-09-03 #77 ① — 같은 (사용자 · 카테고리 · 연월) 예산이 하나로 접히는지 <b>진짜 SQL 로</b> 확인한다.
 *
 * <p>단위 테스트는 리포지토리가 mock 이라 "전체 예산(category IS NULL)" 분기를 아무것도 지키지
 * 못한다. JPA 파생 쿼리에서 {@code = null} 은 어떤 행도 잡지 못하는데, mock 은 그걸 모르고
 * 시키는 대로 행을 내준다 — 그래서 이 검증만 H2 에 붙인다.
 *
 * <p>트랜잭션을 걸지 않는다. {@code createBudget} 은 시도마다 {@code REQUIRES_NEW} 로 자기
 * 트랜잭션을 여는데, 테스트가 트랜잭션을 들고 있으면 커밋 경계가 어긋나 실제 동작과 달라진다.
 * 대신 사용자·연도를 케이스마다 새로 만들어 서로 안 밟게 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseBudgetUpsertIntegrationTest {

    @Autowired private ExpenseBudgetService expenseBudgetService;
    @Autowired private ExpenseBudgetRepository expenseBudgetRepository;
    @Autowired private ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private User newUser() {
        String id = "u" + UUID.randomUUID().toString().substring(0, 8);
        return transactionTemplate.execute(s ->
            userRepository.save(User.createUser(null, id, "테스터", id + "@porest.com")));
    }

    private ExpenseCategory newRootCategory(User owner) {
        return transactionTemplate.execute(s -> expenseCategoryRepository.save(
            ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, null)));
    }

    private ExpenseBudgetServiceDto.CreateCommand cmd(User u, Long categoryRowId, long amount, int year) {
        return new ExpenseBudgetServiceDto.CreateCommand(u.getRowId(), categoryRowId, amount, year, 6);
    }

    private List<ExpenseBudget> rowsOf(User u, int year) {
        return transactionTemplate.execute(s -> expenseBudgetRepository.findByUser(u.getRowId(), year, 6));
    }

    @Test
    @DisplayName("전체 예산을 같은 달에 두 번 등록해도 행은 하나, 금액은 나중 값이다")
    void overallBudgetPostedTwiceKeepsOneRow() {
        User u = newUser();

        var first = expenseBudgetService.createBudget(cmd(u, null, 300_000L, 2101));
        var second = expenseBudgetService.createBudget(cmd(u, null, 500_000L, 2101));

        List<ExpenseBudget> rows = rowsOf(u, 2101);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBudgetAmount()).isEqualTo(500_000L);
        // 두 응답이 같은 행을 가리킨다 — 두 번째가 새 행을 만들지 않았다는 뜻이다.
        assertThat(second.rowId()).isEqualTo(first.rowId());
        assertThat(second.categoryRowId()).isNull();
    }

    @Test
    @DisplayName("카테고리 예산도 두 번 등록하면 행 하나로 접힌다")
    void categoryBudgetPostedTwiceKeepsOneRow() {
        User u = newUser();
        ExpenseCategory root = newRootCategory(u);

        var first = expenseBudgetService.createBudget(cmd(u, root.getRowId(), 100_000L, 2102));
        var second = expenseBudgetService.createBudget(cmd(u, root.getRowId(), 200_000L, 2102));

        List<ExpenseBudget> rows = rowsOf(u, 2102);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBudgetAmount()).isEqualTo(200_000L);
        assertThat(second.rowId()).isEqualTo(first.rowId());
    }

    @Test
    @DisplayName("전체 예산과 카테고리 예산은 서로 다른 행이다 — 접기가 과하게 걸리지 않는다")
    void overallAndCategoryBudgetsAreSeparateRows() {
        User u = newUser();
        ExpenseCategory root = newRootCategory(u);

        expenseBudgetService.createBudget(cmd(u, null, 300_000L, 2103));
        expenseBudgetService.createBudget(cmd(u, root.getRowId(), 100_000L, 2103));

        assertThat(rowsOf(u, 2103)).hasSize(2);
    }

    @Test
    @DisplayName("지운 뒤 다시 등록하면 새 행이 생긴다 — 예산에는 삭제 플래그가 없어 행이 통째로 사라진다")
    void deletedBudgetCanBeCreatedAgain() {
        User u = newUser();

        var created = expenseBudgetService.createBudget(cmd(u, null, 300_000L, 2104));
        expenseBudgetService.deleteBudget(created.rowId(), u.getRowId());

        var again = expenseBudgetService.createBudget(cmd(u, null, 400_000L, 2104));

        List<ExpenseBudget> rows = rowsOf(u, 2104);
        assertThat(rows).hasSize(1);
        assertThat(again.rowId()).isNotEqualTo(created.rowId());
        assertThat(rows.get(0).getBudgetAmount()).isEqualTo(400_000L);
    }

    /**
     * 서비스의 복구 경로가 잡는 예외 타입을 못 박는다.
     *
     * <p>{@code @Repository} 빈은 {@code PersistenceExceptionTranslationPostProcessor} 가
     * 감싸므로 UK 위반이 하이버네이트 {@code ConstraintViolationException} 이 아니라 스프링
     * {@link DataIntegrityViolationException} 으로 나온다. 이게 아니면
     * {@code createBudget} 의 {@code catch} 가 아무것도 못 잡고 경쟁은 500 이 된다.
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 그 후처리기가 없어 번역이 안 된다 — 그래서 이
     * 검증만은 전체 컨텍스트여야 한다(확인함).
     */
    @Test
    @DisplayName("UK 위반은 DataIntegrityViolationException 으로 올라온다 — 복구 경로가 잡는 타입")
    void ukViolationSurfacesAsDataIntegrityViolation() {
        User u = newUser();
        transactionTemplate.execute(s -> entityManager.createNativeQuery(
            "CREATE UNIQUE INDEX uk_budget_probe ON expense_budget "
                + "(user_row_id, budget_year, budget_month)").executeUpdate());
        try {
            expenseBudgetService.createBudget(cmd(u, null, 300_000L, 2105));

            assertThatThrownBy(() -> transactionTemplate.execute(s -> expenseBudgetRepository.save(
                ExpenseBudget.createBudget(u, null, 400_000L, 2105, 6))))
                .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            // 이 인덱스는 스키마에 없는 것이고 H2 는 JVM 내내 살아 있다(DB_CLOSE_DELAY=-1).
            // 남겨 두면 다른 테스트의 예산 저장을 엉뚱하게 막는다.
            transactionTemplate.execute(s ->
                entityManager.createNativeQuery("DROP INDEX uk_budget_probe").executeUpdate());
        }
    }
}
