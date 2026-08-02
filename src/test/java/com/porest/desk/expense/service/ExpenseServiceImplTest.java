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
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.desk.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.porest.desk.common.time.ServiceClock;
import com.porest.desk.common.time.UserClock;

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
    @Mock private ExpenseSplitService expenseSplitService;
    @Mock private NotificationService notificationService;
    @Mock private UserService userService;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(
            org.mockito.Mockito.mock(com.porest.desk.user.repository.UserRepository.class),
            new ServiceClock("Asia/Seoul"));

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
                "점심", LocalDateTime.of(2026, 6, 1, 12, 0), "식당", "CARD", null, null, null);
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
    @DisplayName("createExpense — 거래 유형 ≠ 카테고리 유형이면 거부(타입 일치 강제)")
    void createRejectsTypeMismatch() {
        User u = user(USER_ID);
        ExpenseCategory expenseCat = category(10L, u); // EXPENSE 타입 카테고리
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(expenseCat));

        // INCOME 거래를 EXPENSE 카테고리에 등록 시도
        var cmd = new ExpenseServiceDto.CreateCommand(
                USER_ID, 10L, null, ExpenseType.INCOME, 10_000L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null);

        assertThatThrownBy(() -> sut.createExpense(cmd))
                .isInstanceOf(InvalidValueException.class);
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
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), "식당", "CARD", null, null, null);

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
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null, null);

        sut.updateExpense(5L, USER_ID, cmd);

        verify(notificationService).createNotification(any());
    }

    // ── 자산 잔액 이력 연동(잔액 정확성 와이어링) ─────────────────────────────
    @Test
    @DisplayName("updateExpense — 잔액 이력은 removeExpense 먼저, 그 다음 recordExpense 순서로 재적재")
    void updateExpenseRemoveThenRecordInOrder() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, 1_000L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);

        sut.updateExpense(5L, USER_ID, updateCmd(10L));

        InOrder ord = inOrder(balanceHistoryService);
        ord.verify(balanceHistoryService).removeExpense(5L);                       // 먼저 기존 flow 제거
        ord.verify(balanceHistoryService).recordExpense(any(), eq(5L), any(), any(), any()); // 그 다음 재적재
    }

    @Test
    @DisplayName("deleteExpense — removeExpense 만 호출(recordExpense 없음)")
    void deleteExpenseRemovesOnly() {
        Expense expense = mock(Expense.class);
        given(expense.getUser()).willReturn(user(USER_ID));
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));

        sut.deleteExpense(5L, USER_ID);

        verify(balanceHistoryService).removeExpense(5L);
        verify(balanceHistoryService, never()).recordExpense(any(), any(), any(), any(), any());
    }

    // ── 예산 임계 경계 (정상 동작 정확성) ─────────────────────────────
    private ExpenseServiceDto.CreateCommand createCmdAmount(long categoryRowId, long amount) {
        return new ExpenseServiceDto.CreateCommand(
                USER_ID, categoryRowId, null, ExpenseType.EXPENSE, amount,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null);
    }

    private void givenOverallBudget(long limit, int warnPct, User u, ExpenseCategory leaf, long monthlyTotal) {
        // 실제 ExpenseBudget(전체, category=null) 사용 — mock 불필요 스터빙 회피
        ExpenseBudget budget = ExpenseBudget.createBudget(u, null, limit, 2026, 6);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), eq(2026), eq(6))).willReturn(List.of(budget));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(warnPct);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(
                Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, monthlyTotal,
                        "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null)));
    }

    @Test
    @DisplayName("createExpense — 정확히 85%(8,500/10,000)에서 warn 알림 발생")
    void createWarnAtExactly85() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        givenOverallBudget(10_000L, 85, u, leaf, 8_500L);

        sut.createExpense(createCmdAmount(10L, 8_500L));

        verify(notificationService, times(1)).createNotification(any()); // 0→0.85 돌파
    }

    @Test
    @DisplayName("createExpense — 84.99%(8,499)에서는 알림 없음(경계 1원 미달)")
    void createNoAlertJustBelow85() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        givenOverallBudget(10_000L, 85, u, leaf, 8_499L);

        sut.createExpense(createCmdAmount(10L, 8_499L));

        verify(notificationService, never()).createNotification(any()); // 0.8499 < 0.85
    }

    @Test
    @DisplayName("createExpense — 99.5%(9,950) WARN 라벨은 '100%' 아닌 '99%' 로 표기(반올림 cap)")
    void createWarnLabelCappedAt99() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        givenOverallBudget(10_000L, 85, u, leaf, 9_950L); // 99.5% — OVER 아님(<100%)

        sut.createExpense(createCmdAmount(10L, 9_950L));

        ArgumentCaptor<NotificationServiceDto.CreateCommand> captor =
                ArgumentCaptor.forClass(NotificationServiceDto.CreateCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().title()).contains("99%").doesNotContain("100%");
    }

    @Test
    @DisplayName("createExpense — 0→100% 점프 시 OVER 우선(알림 정확히 1건, WARN 중복 아님)")
    void createOverPriorityAt100() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        givenOverallBudget(10_000L, 85, u, leaf, 10_000L);

        sut.createExpense(createCmdAmount(10L, 10_000L));

        // if(OVER) else-if(WARN) → 85·100 동시 초과여도 알림은 1건만(OVER)
        verify(notificationService, times(1)).createNotification(any());
    }

    @Test
    @DisplayName("updateExpense — 99.99%(9,900→9,999)에서는 초과 알림 없음")
    void updateNoOverJustBelow100() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, 9_900L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        ExpenseBudget budget = ExpenseBudget.createBudget(u, null, 10_000L, 2026, 6);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), eq(2026), eq(6))).willReturn(List.of(budget));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(85);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(expense));

        var cmd = new ExpenseServiceDto.UpdateCommand(
                10L, null, ExpenseType.EXPENSE, 9_999L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null, null);
        sut.updateExpense(5L, USER_ID, cmd);

        // before 0.99, after 0.9999: OVER(>=1.0) 거짓, WARN(이미 0.99>=0.85) 미돌파 → 알림 없음
        verify(notificationService, never()).createNotification(any());
    }

    @Test
    @DisplayName("updateExpense — 정확히 100%(9,900→10,000)에서 초과 알림 발생")
    void updateOverAtExactly100() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, 9_900L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        ExpenseBudget budget = ExpenseBudget.createBudget(u, null, 10_000L, 2026, 6);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), eq(2026), eq(6))).willReturn(List.of(budget));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(85);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(expense));

        var cmd = new ExpenseServiceDto.UpdateCommand(
                10L, null, ExpenseType.EXPENSE, 10_000L,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null, null);
        sut.updateExpense(5L, USER_ID, cmd);

        verify(notificationService, times(1)).createNotification(any()); // before 0.99<1.0, after 1.0>=1.0 → OVER
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
        verify(balanceHistoryService).recordExpense(any(), any(), eq(ExpenseType.EXPENSE), eq(10_000L), any(), eq(true));
    }

    @Test
    @DisplayName("getMonthlyTrend — 음수 months 는 거부")
    void getMonthlyTrendRejectsNegativeMonths() {
        assertThatThrownBy(() -> sut.getMonthlyTrend(USER_ID, -3))
                .isInstanceOf(InvalidValueException.class);
    }

    // ── 분할 합 일치화 (거래 금액 ↔ 분할 합 불변식) ─────────────────────────────
    private Expense expenseWithRowId(User u, ExpenseCategory leaf, long amount) {
        Expense expense = Expense.createExpense(u, leaf, null, ExpenseType.EXPENSE, amount,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        return expense;
    }

    private ExpenseServiceDto.UpdateCommand updateCmdWithSplits(
            long categoryRowId, long amount, List<ExpenseSplitServiceDto.SplitCommand> splits) {
        return new ExpenseServiceDto.UpdateCommand(
                categoryRowId, null, ExpenseType.EXPENSE, amount,
                "x", LocalDateTime.of(2026, 6, 1, 12, 0), null, null, null, null, splits);
    }

    @Test
    @DisplayName("updateExpense — 금액을 바꿔 기존 분할 합과 어긋나는데 분할을 함께 안 보내면 거부")
    void updateRejectsAmountChangeDesyncingExistingSplits() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = expenseWithRowId(u, leaf, 10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        // 기존 분할 합 10,000 (6,000 + 4,000)
        given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of(
                ExpenseSplit.create(expense, leaf, 6_000L, "a", 0),
                ExpenseSplit.create(expense, leaf, 4_000L, "b", 1)));

        // 금액을 20,000 으로 바꾸면서 분할 미전달(null) → 합 10,000 ≠ 20,000 → 거부
        assertThatThrownBy(() -> sut.updateExpense(5L, USER_ID, updateCmdWithSplits(10L, 20_000L, null)))
                .isInstanceOf(InvalidValueException.class);
        verify(expenseSplitService, never()).replaceSplits(any());
    }

    @Test
    @DisplayName("updateExpense — 분할 합이 새 금액과 일치하면(미전달) 그대로 허용")
    void updateAllowsWhenExistingSplitSumMatches() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = expenseWithRowId(u, leaf, 10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of(
                ExpenseSplit.create(expense, leaf, 6_000L, "a", 0),
                ExpenseSplit.create(expense, leaf, 4_000L, "b", 1)));

        // 금액 10,000 유지 → 분할 합 10,000 == 10,000 → 허용, 분할 교체 호출 없음
        var info = sut.updateExpense(5L, USER_ID, updateCmdWithSplits(10L, 10_000L, null));

        assertThat(info.amount()).isEqualTo(10_000L);
        verify(expenseSplitService, never()).replaceSplits(any());
    }

    @Test
    @DisplayName("updateExpense — 맞춘 분할을 함께 보내면 원자적으로 교체(replaceSplits 위임)")
    void updateWithReconciledSplitsReplacesAtomically() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = expenseWithRowId(u, leaf, 10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);

        // 20,000 으로 상향하면서 합이 20,000 인 분할을 함께 전달
        var splits = List.of(
                new ExpenseSplitServiceDto.SplitCommand(10L, 12_000L, "a", 0),
                new ExpenseSplitServiceDto.SplitCommand(10L, 8_000L, "b", 1));

        sut.updateExpense(5L, USER_ID, updateCmdWithSplits(10L, 20_000L, splits));

        ArgumentCaptor<ExpenseSplitServiceDto.ReplaceCommand> captor =
                ArgumentCaptor.forClass(ExpenseSplitServiceDto.ReplaceCommand.class);
        verify(expenseSplitService).replaceSplits(captor.capture());
        assertThat(captor.getValue().expenseRowId()).isEqualTo(5L);
        assertThat(captor.getValue().userRowId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().splits()).hasSize(2);
        // 합 검증은 replaceSplits 책임 → 위임만 확인(미전달 분기 가드 미진입)
        verify(expenseSplitRepository, never()).findByExpense(any());
    }

    @Test
    @DisplayName("updateExpense — 분할이 없는 거래는 금액만 바꿔도 통과(불변식 무관)")
    void updateWithoutExistingSplitsPasses() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        Expense expense = expenseWithRowId(u, leaf, 10_000L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(expenseSplitRepository.findByExpense(5L)).willReturn(List.of()); // 분할 없음

        var info = sut.updateExpense(5L, USER_ID, updateCmdWithSplits(10L, 20_000L, null));

        assertThat(info.amount()).isEqualTo(20_000L);
        verify(expenseSplitService, never()).replaceSplits(any());
    }

    // ── split-aware 카테고리 집계 (B안: 분할은 분할 leaf+부모로 귀속) ─────────────
    private ExpenseCategory leafUnder(long rowId, User u, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(u, "leaf" + rowId, "t", "#fff", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseCategory parentCat(long rowId, User u, String name) {
        ExpenseCategory c = ExpenseCategory.createCategory(u, name, "t", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    @Test
    @DisplayName("getMonthlyExpenseSpendByCategory — 분할은 분할 카테고리 leaf+부모로 귀속(다른 부모 트리로 분산)")
    void monthlySpendIsSplitAwareWithParentRollup() {
        User u = user(USER_ID);
        ExpenseCategory p1 = parentCat(1L, u, "식비/음료");
        ExpenseCategory l2 = leafUnder(2L, u, p1);   // 식비
        ExpenseCategory p7 = parentCat(7L, u, "생활");
        ExpenseCategory l8 = leafUnder(8L, u, p7);   // 생활용품

        // A: 비분할 거래 5,000 (식비 l2)
        Expense a = Expense.createExpense(u, l2, null, ExpenseType.EXPENSE, 5_000L, "a",
                LocalDateTime.of(2026, 6, 3, 12, 0), null, null);
        ReflectionTestUtils.setField(a, "rowId", 100L);
        // B: 쿠팡 10,000, 분할 [생활용품 6,000 + 식비 4,000] (선언 카테고리는 l8)
        Expense b = Expense.createExpense(u, l8, null, ExpenseType.EXPENSE, 10_000L, "쿠팡",
                LocalDateTime.of(2026, 6, 5, 12, 0), null, null);
        ReflectionTestUtils.setField(b, "rowId", 200L);
        ExpenseSplit b1 = ExpenseSplit.create(b, l8, 6_000L, "생활", 0);
        ExpenseSplit b2 = ExpenseSplit.create(b, l2, 4_000L, "식비", 1);

        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(a, b));
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of(b1, b2));

        Map<Long, Long> spend = sut.getMonthlyExpenseSpendByCategory(USER_ID, 2026, 6);

        // 식비 leaf(2) = A 5,000 + B분할 4,000 = 9,000 → 부모 식비/음료(1) 9,000
        // 생활용품 leaf(8) = B분할 6,000 → 부모 생활(7) 6,000
        assertThat(spend).containsEntry(2L, 9_000L).containsEntry(1L, 9_000L)
                .containsEntry(8L, 6_000L).containsEntry(7L, 6_000L)
                .hasSize(4);
    }

    @Test
    @DisplayName("updateExpense — 다른 카테고리로 분할하면 그 카테고리(부모) 예산 초과 알림 발생(분할 무시 회귀 방지)")
    void updateWithCrossCategorySplitAlertsSplitCategoryBudget() {
        User u = user(USER_ID);
        ExpenseCategory p1 = parentCat(1L, u, "식비/음료");
        ExpenseCategory l2 = leafUnder(2L, u, p1);   // 식비
        ExpenseCategory p7 = parentCat(7L, u, "생활");
        ExpenseCategory l8 = leafUnder(8L, u, p7);   // 생활용품(거래 선언 카테고리)

        // 거래: 선언 카테고리 생활용품(8), 10,000원, 분할 없음 상태에서 시작.
        Expense expense = Expense.createExpense(u, l8, null, ExpenseType.EXPENSE, 10_000L, "쿠팡",
                LocalDateTime.of(2026, 6, 5, 12, 0), null, null);
        ReflectionTestUtils.setField(expense, "rowId", 5L);
        given(expenseRepository.findById(5L)).willReturn(Optional.of(expense));
        given(expenseCategoryRepository.findById(8L)).willReturn(Optional.of(l8));
        given(expenseCategoryRepository.hasChildren(8L)).willReturn(false);

        // 식비/음료(부모 1) 예산 4,000원, warn 85%
        ExpenseBudget budget = ExpenseBudget.createBudget(u, p1, 4_000L, 2026, 6);
        ReflectionTestUtils.setField(budget, "rowId", 1L);
        given(expenseBudgetRepository.findByUser(eq(USER_ID), eq(2026), eq(6))).willReturn(List.of(budget));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(85);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any())).willReturn(List.of(expense));

        // findByExpenseIds: 1번째(변경 전 기여분 캡처)=분할 없음, 2번째(notify)=새 분할 [식비 4,000 + 생활용품 6,000]
        ExpenseSplit sp2 = ExpenseSplit.create(expense, l2, 4_000L, "식비", 0);
        ExpenseSplit sp8 = ExpenseSplit.create(expense, l8, 6_000L, "생활", 1);
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of(), List.of(sp2, sp8));

        var cmd = new ExpenseServiceDto.UpdateCommand(
                8L, null, ExpenseType.EXPENSE, 10_000L,
                "쿠팡", LocalDateTime.of(2026, 6, 5, 12, 0), null, null, null, null,
                List.of(new ExpenseSplitServiceDto.SplitCommand(2L, 4_000L, "식비", 0),
                        new ExpenseSplitServiceDto.SplitCommand(8L, 6_000L, "생활", 1)));

        sut.updateExpense(5L, USER_ID, cmd);

        // 식비/음료(1) 예산이 분할 4,000 으로 100% 도달 → OVER 알림. (구 로직은 전액을 생활용품에만 귀속해 누락)
        ArgumentCaptor<NotificationServiceDto.CreateCommand> captor =
                ArgumentCaptor.forClass(NotificationServiceDto.CreateCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().title()).contains("식비/음료");
    }

    @Test
    @DisplayName("getExpenses — 분할 거래는 splitCategoryRowIds 에 분할 카테고리 id 를 노출(목록 필터 split-aware용)")
    void getExpensesExposesSplitCategoryIds() {
        User u = user(USER_ID);
        ExpenseCategory p7 = parentCat(7L, u, "생활");
        ExpenseCategory l8 = leafUnder(8L, u, p7);
        ExpenseCategory p1 = parentCat(1L, u, "식비/음료");
        ExpenseCategory l2 = leafUnder(2L, u, p1);
        Expense e = Expense.createExpense(u, l8, null, ExpenseType.EXPENSE, 10_000L, "쿠팡",
                LocalDateTime.of(2026, 6, 5, 12, 0), null, null);
        ReflectionTestUtils.setField(e, "rowId", 50L);
        given(expenseRepository.findByUser(eq(USER_ID), any(), any(), any(), any())).willReturn(List.of(e));
        ExpenseSplit s1 = ExpenseSplit.create(e, l8, 6_000L, "생활", 0);
        ExpenseSplit s2 = ExpenseSplit.create(e, l2, 4_000L, "식비", 1);
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of(s1, s2));

        var result = sut.getExpenses(USER_ID, null, null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).splitCategoryRowIds()).containsExactlyInAnyOrder(8L, 2L);
    }

    @Test
    @DisplayName("searchExpenses — 분할 거래는 splitCategoryRowIds 노출(검색 필터 split-aware용, 목록과 대칭)")
    void searchExposesSplitCategoryIds() {
        User u = user(USER_ID);
        ExpenseCategory p7 = parentCat(7L, u, "생활");
        ExpenseCategory l8 = leafUnder(8L, u, p7);
        ExpenseCategory p1 = parentCat(1L, u, "식비/음료");
        ExpenseCategory l2 = leafUnder(2L, u, p1);
        Expense e = Expense.createExpense(u, l8, null, ExpenseType.EXPENSE, 10_000L, "쿠팡",
                LocalDateTime.of(2026, 6, 5, 12, 0), null, null);
        ReflectionTestUtils.setField(e, "rowId", 60L);
        given(expenseRepository.search(eq(USER_ID), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(e));
        ExpenseSplit s1 = ExpenseSplit.create(e, l8, 6_000L, "생활", 0);
        ExpenseSplit s2 = ExpenseSplit.create(e, l2, 4_000L, "식비", 1);
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of(s1, s2));

        var cmd = new ExpenseServiceDto.SearchCommand(
                USER_ID, null, null, null, "쿠팡", null, null, null, null, null);
        var result = sut.searchExpenses(cmd);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).splitCategoryRowIds()).containsExactlyInAnyOrder(8L, 2L);
    }
}
