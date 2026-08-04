package com.porest.desk.expense.service;

import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 예산 이행률 집계 로직 회귀 방지 테스트 — 의도된 동작 검증:
 *  - 전체 상한(category=null)이 있으면 그것만 한도로 사용(카테고리 합과 중복집계 금지)
 *  - 전체 상한이 없을 때만 카테고리별 한도 합을 한도로 사용
 *  - 한도 0 이면 이행률 0 (0 나눗셈 방지)
 */
@ExtendWith(MockitoExtension.class)
class ExpenseBudgetServiceComplianceTest {

    @Mock private ExpenseBudgetRepository expenseBudgetRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private ExpenseBudgetServiceImpl sut;

    private static final long USER_ID = 1L;

    private ExpenseBudget overallBudget(long amount) {
        return ExpenseBudget.createBudget(null, null, amount, 2026, 6);
    }

    private ExpenseBudget categoryBudget(long amount) {
        ExpenseCategory cat = ExpenseCategory.createCategory(null, "식비", "tag", "#fff", ExpenseType.EXPENSE, null);
        return ExpenseBudget.createBudget(null, cat, amount, 2026, 6);
    }

    private Expense expense(ExpenseType type, long amount) {
        return Expense.createExpense(null, null, null, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), null, null, null);
    }

    private void givenBudgets(List<ExpenseBudget> budgets) {
        given(expenseBudgetRepository.findByUser(eq(USER_ID), anyInt(), anyInt())).willReturn(budgets);
    }

    private void givenExpenses(List<Expense> expenses) {
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(expenses);
    }

    @Test
    @DisplayName("전체 상한이 있으면 카테고리 합이 아니라 전체 상한을 한도로 쓴다(중복집계 방지)")
    void overallLimitTakesPrecedence() {
        givenBudgets(List.of(overallBudget(100_000L), categoryBudget(50_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 80_000L)));

        List<ExpenseBudgetServiceDto.ComplianceMonth> result = sut.getCompliance(USER_ID, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalLimit()).isEqualTo(100_000L); // 150,000 아님
        assertThat(result.get(0).totalSpent()).isEqualTo(80_000L);
        assertThat(result.get(0).compliancePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("전체 상한이 없으면 카테고리별 한도의 합을 한도로 쓴다")
    void categorySumWhenNoOverall() {
        givenBudgets(List.of(categoryBudget(30_000L), categoryBudget(20_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 25_000L)));

        var result = sut.getCompliance(USER_ID, 1);

        assertThat(result.get(0).totalLimit()).isEqualTo(50_000L);
        assertThat(result.get(0).compliancePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("수입은 지출 합계에서 제외된다")
    void incomeExcludedFromSpent() {
        givenBudgets(List.of(overallBudget(100_000L)));
        givenExpenses(List.of(
                expense(ExpenseType.EXPENSE, 40_000L),
                expense(ExpenseType.INCOME, 500_000L) // 제외
        ));

        var result = sut.getCompliance(USER_ID, 1);

        assertThat(result.get(0).totalSpent()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("한도가 0이면 이행률 0 (0 나눗셈 방지)")
    void zeroLimitGivesZeroPercent() {
        givenBudgets(List.of());
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 10_000L)));

        var result = sut.getCompliance(USER_ID, 1);

        assertThat(result.get(0).totalLimit()).isEqualTo(0L);
        assertThat(result.get(0).compliancePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("이행률 — 8,500/10,000 = 85.0%")
    void compliance85() {
        givenBudgets(List.of(overallBudget(10_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 8_500L)));

        var result = sut.getCompliance(USER_ID, 1);

        assertThat(result.get(0).totalLimit()).isEqualTo(10_000L);
        assertThat(result.get(0).totalSpent()).isEqualTo(8_500L);
        assertThat(result.get(0).compliancePercent()).isEqualTo(85.0); // 8500/10000=0.85
    }

    @Test
    @DisplayName("이행률 0.1% 반올림 — 8,450/10,000 = 84.5% (명확한 내림 경계)")
    void complianceRoundsToOneDecimal() {
        givenBudgets(List.of(overallBudget(10_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 8_450L)));

        var result = sut.getCompliance(USER_ID, 1);

        // round(845.0)/10 = 84.5
        assertThat(result.get(0).compliancePercent()).isEqualTo(84.5);
    }

    @Test
    @DisplayName("이행률 100% 경계 — 10,000/10,000=100.0, 9,994/10,000=99.9")
    void compliance100Boundary() {
        givenBudgets(List.of(overallBudget(10_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 10_000L)));
        assertThat(sut.getCompliance(USER_ID, 1).get(0).compliancePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("이행률 — 9,994/10,000 = 99.9% (round(999.4)/10)")
    void complianceJustBelow100() {
        givenBudgets(List.of(overallBudget(10_000L)));
        givenExpenses(List.of(expense(ExpenseType.EXPENSE, 9_994L)));
        assertThat(sut.getCompliance(USER_ID, 1).get(0).compliancePercent()).isEqualTo(99.9);
    }
}
