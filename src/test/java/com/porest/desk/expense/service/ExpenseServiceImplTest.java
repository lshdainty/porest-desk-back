package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.desk.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 거래(Expense) 정책 회귀 방지 단위 테스트.
 *
 * <p>핵심 정책: 거래는 leaf 카테고리(자식 없는)에만 — 자식 보유(상위) 카테고리는 거래 불가.
 * + 카테고리/거래 소유권 검증.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseBudgetRepository expenseBudgetRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserService userService;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ExpenseServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseServiceDto.CreateCommand createCmd(long categoryRowId) {
        return new ExpenseServiceDto.CreateCommand(
                USER_ID, categoryRowId, null, ExpenseType.EXPENSE, 10_000L,
                "점심", LocalDateTime.of(2026, 6, 1, 12, 0), "식당", "CARD", null, null);
    }

    private ExpenseServiceDto.UpdateCommand updateCmd(long categoryRowId) {
        return new ExpenseServiceDto.UpdateCommand(
                categoryRowId, null, ExpenseType.EXPENSE, 10_000L,
                "점심", LocalDateTime.of(2026, 6, 1, 12, 0), "식당", "CARD", null, null);
    }

    @Test
    @DisplayName("createExpense — 자식 보유(상위) 카테고리에는 거래 불가")
    void createRejectsNonLeafCategory() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

        assertThatThrownBy(() -> sut.createExpense(createCmd(10L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createExpense — 남의 카테고리에는 거래 불가")
    void createRejectsOthersCategory() {
        User u = user(USER_ID);
        ExpenseCategory othersCategory = category(20L, user(999L));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> sut.createExpense(createCmd(20L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateExpense — 자식 보유(상위) 카테고리로 변경 불가")
    void updateRejectsNonLeafCategory() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        ExpenseCategory parent = category(30L, u);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(30L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(30L)).willReturn(true);

        assertThatThrownBy(() -> sut.updateExpense(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("updateExpense — 남의 거래는 수정 불가")
    void updateRejectsOthersExpense() {
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(user(999L));
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));

        assertThatThrownBy(() -> sut.updateExpense(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateExpense — 남의 자산으로 변경 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersAsset() {
        User u = user(USER_ID);
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(u);
        ExpenseCategory leaf = category(10L, u);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        Asset othersAsset = mock(Asset.class);
        given(othersAsset.getUser()).willReturn(user(999L));
        given(assetRepository.findById(20L)).willReturn(Optional.of(othersAsset));

        var cmd = new ExpenseServiceDto.UpdateCommand(
                10L, 20L, ExpenseType.EXPENSE, 10_000L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), "식당", "CARD", null, null);

        assertThatThrownBy(() -> sut.updateExpense(5L, USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getExpenses — 시작일이 종료일보다 늦으면 거부(역전 범위 조용한 빈결과 방지)")
    void getExpensesRejectsInvertedDateRange() {
        assertThatThrownBy(() -> sut.getExpenses(USER_ID, null, null, null,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("getRangeSummary — 시작일이 종료일보다 늦으면 거부")
    void getRangeSummaryRejectsInvertedDateRange() {
        assertThatThrownBy(() -> sut.getRangeSummary(USER_ID,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("getRangeSummary — 자식 카테고리가 다른 부모로 이동해도 합계는 leaf 기준으로 정확하다(부모 라벨만 변경)")
    void rangeSummaryAggregatesByLeafRegardlessOfParent() {
        User u = user(USER_ID);
        ExpenseCategory parent1 = ExpenseCategory.createCategory(u, "부모1", "t", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(parent1, "rowId", 100L);
        ExpenseCategory parent2 = ExpenseCategory.createCategory(u, "부모2", "t", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(parent2, "rowId", 200L);
        ExpenseCategory child = ExpenseCategory.createCategory(u, "식비", "t", "#fff", ExpenseType.EXPENSE, parent1);
        ReflectionTestUtils.setField(child, "rowId", 10L);

        Expense e1 = Expense.createExpense(u, child, null, ExpenseType.EXPENSE, 3_000L, "a",
                LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(e1, "rowId", 1L);
        Expense e2 = Expense.createExpense(u, child, null, ExpenseType.EXPENSE, 2_000L, "b",
                LocalDateTime.of(2026, 6, 2, 12, 0), null, null);
        ReflectionTestUtils.setField(e2, "rowId", 2L);

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        given(expenseRepository.findByDateRange(USER_ID, start, end)).willReturn(List.of(e1, e2));
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of());

        // 부모1 산하일 때
        var before = sut.getRangeSummary(USER_ID, start, end);
        assertThat(before.totalExpense()).isEqualTo(5_000L);
        assertThat(before.categoryBreakdown()).hasSize(1);
        var b0 = before.categoryBreakdown().get(0);
        assertThat(b0.categoryRowId()).isEqualTo(10L);
        assertThat(b0.totalAmount()).isEqualTo(5_000L);
        assertThat(b0.parentCategoryRowId()).isEqualTo(100L);

        // 부모2 산하로 이동 — 합계/leaf 집계는 그대로, 부모 라벨만 변경(이중계상·누락 없음)
        child.moveParent(parent2);
        var after = sut.getRangeSummary(USER_ID, start, end);
        assertThat(after.totalExpense()).isEqualTo(5_000L);
        assertThat(after.categoryBreakdown()).hasSize(1);
        var a0 = after.categoryBreakdown().get(0);
        assertThat(a0.categoryRowId()).isEqualTo(10L);
        assertThat(a0.totalAmount()).isEqualTo(5_000L);
        assertThat(a0.parentCategoryRowId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("updateExpense — 금액 상향으로 예산 임계를 넘으면 알림이 발생한다(수정 경로 알림 누락 보강)")
    void updateCrossingBudgetThresholdNotifies() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        // 수정 전 1,000원 거래(예산 10,000 중 10%)
        Expense expense = Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, 1_000L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);

        // 전체 예산(카테고리 null) 10,000원, warn 임계 85%
        ExpenseBudget budget = mock(ExpenseBudget.class);
        given(budget.getBudgetAmount()).willReturn(10_000L);
        given(budget.getCategory()).willReturn(null);
        given(budget.getRowId()).willReturn(1L);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), eq(2026), eq(6))).willReturn(List.of(budget));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(85);
        // 월간 집계: 수정된 이 거래(9,900)만 존재 → 99% 사용
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(expense));

        // 1,000 → 9,900 으로 상향 수정
        var cmd = new ExpenseServiceDto.UpdateCommand(
                10L, null, ExpenseType.EXPENSE, 9_900L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null);

        sut.updateExpense(5L, USER_ID, cmd);

        verify(notificationService).createNotification(any());
    }

    @Test
    @DisplayName("createExpense — 성공 시 거래를 저장하고 자산 잔액 이력을 기록한다")
    void createPersistsAndRecordsBalance() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), anyInt(), anyInt())).willReturn(List.of());

        var info = sut.createExpense(createCmd(10L));

        assertThat(info.amount()).isEqualTo(10_000L);
        verify(expenseRepository).save(any(Expense.class));
        verify(balanceHistoryService).recordExpense(any(), any(), eq(ExpenseType.EXPENSE), eq(10_000L), any());
    }
}
