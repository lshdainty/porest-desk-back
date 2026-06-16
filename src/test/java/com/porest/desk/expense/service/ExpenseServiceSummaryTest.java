package com.porest.desk.expense.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.todo.repository.TodoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

/**
 * 거래 집계·필터 로직 회귀 방지 단위 테스트 — 일별 합계, 거래처별 그룹 합계/필터/정렬.
 * 실제 Expense 엔티티로 서비스의 변환 로직만 검증(레포는 mock).
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceSummaryTest {

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

    private Expense expense(ExpenseType type, long amount, String merchant) {
        return Expense.createExpense(null, null, null, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), merchant, "CARD");
    }

    private ExpenseCategory category(long rowId, String name, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(null, name, "tag", "#fff", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private Expense expenseIn(ExpenseCategory cat, ExpenseType type, long amount) {
        return Expense.createExpense(null, cat, null, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), null, null);
    }

    private Asset asset(long rowId, String name) {
        Asset a = Asset.createAsset(null, name, AssetType.BANK_ACCOUNT, 0L, "KRW",
                null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private Expense expenseWithAsset(Asset asset, ExpenseType type, long amount) {
        return Expense.createExpense(null, null, asset, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), null, null);
    }

    @Test
    @DisplayName("getDailySummary — 수입/지출을 각각 합산한다")
    void dailySummarySums() {
        LocalDate date = LocalDate.of(2026, 6, 15);
        given(expenseRepository.findDailySummary(USER_ID, date)).willReturn(List.of(
                expense(ExpenseType.INCOME, 100_000L, null),
                expense(ExpenseType.EXPENSE, 30_000L, null),
                expense(ExpenseType.EXPENSE, 12_000L, null)
        ));

        ExpenseServiceDto.DailySummary summary = sut.getDailySummary(USER_ID, date);

        assertThat(summary.totalIncome()).isEqualTo(100_000L);
        assertThat(summary.totalExpense()).isEqualTo(42_000L);
    }

    @Test
    @DisplayName("getMerchantSummary — 지출만, 거래처별 합산·건수·내림차순, 빈 거래처 제외")
    void merchantSummaryGroupsFiltersSorts() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        given(expenseRepository.findByUser(eq(USER_ID), isNull(), isNull(), any(), any()))
                .willReturn(List.of(
                        expense(ExpenseType.EXPENSE, 5_000L, "스타벅스"),
                        expense(ExpenseType.EXPENSE, 3_000L, "스타벅스"),
                        expense(ExpenseType.EXPENSE, 10_000L, "이마트"),
                        expense(ExpenseType.INCOME, 100_000L, "회사"),   // 수입 → 제외
                        expense(ExpenseType.EXPENSE, 2_000L, "   "),     // 빈 거래처 → 제외
                        expense(ExpenseType.EXPENSE, 1_000L, null)        // null 거래처 → 제외
                ));

        List<ExpenseServiceDto.MerchantSummary> result = sut.getMerchantSummary(USER_ID, start, end);

        assertThat(result).hasSize(2);
        // 금액 내림차순: 이마트(10000) > 스타벅스(8000)
        assertThat(result.get(0).merchant()).isEqualTo("이마트");
        assertThat(result.get(0).totalAmount()).isEqualTo(10_000L);
        assertThat(result.get(1).merchant()).isEqualTo("스타벅스");
        assertThat(result.get(1).totalAmount()).isEqualTo(8_000L);
        assertThat(result.get(1).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("getRangeSummary — 자식 카테고리 지출이 부모로 roll-up되어 집계된다")
    void rangeSummaryRollsUpChildToParent() {
        ExpenseCategory parent = category(10L, "건강", null);
        ExpenseCategory child = category(11L, "의료비", parent);
        ExpenseCategory food = category(20L, "식비", null);
        // 분할 없음
        given(expenseSplitRepository.findByExpenseIds(anyList())).willReturn(List.of());
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        expenseIn(child, ExpenseType.EXPENSE, 5_000L),
                        expenseIn(child, ExpenseType.EXPENSE, 3_000L),
                        expenseIn(food, ExpenseType.EXPENSE, 10_000L)
                ));

        ExpenseServiceDto.RangeSummary summary = sut.getRangeSummary(
                USER_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(summary.totalExpense()).isEqualTo(18_000L);

        var childEntry = summary.categoryBreakdown().stream()
                .filter(b -> b.categoryRowId().equals(11L)).findFirst().orElseThrow();
        assertThat(childEntry.totalAmount()).isEqualTo(8_000L);          // 같은 자식 카테고리 합산
        assertThat(childEntry.parentCategoryRowId()).isEqualTo(10L);     // 부모로 roll-up 링크

        var foodEntry = summary.categoryBreakdown().stream()
                .filter(b -> b.categoryRowId().equals(20L)).findFirst().orElseThrow();
        assertThat(foodEntry.parentCategoryRowId()).isNull();           // 최상위는 부모 없음
    }

    @Test
    @DisplayName("getAssetSummary — 지출만, 자산별 합산·건수·내림차순, 자산 없는 거래 제외")
    void assetSummaryGroupsFiltersSorts() {
        Asset bank = asset(1L, "통장");
        Asset card = asset(2L, "카드");
        given(expenseRepository.findByUser(eq(USER_ID), isNull(), isNull(), any(), any()))
                .willReturn(List.of(
                        expenseWithAsset(bank, ExpenseType.EXPENSE, 5_000L),
                        expenseWithAsset(bank, ExpenseType.EXPENSE, 3_000L),
                        expenseWithAsset(card, ExpenseType.EXPENSE, 10_000L),
                        expenseWithAsset(bank, ExpenseType.INCOME, 100_000L), // 수입 → 제외
                        expense(ExpenseType.EXPENSE, 2_000L, null)             // 자산 없음 → 제외
                ));

        List<ExpenseServiceDto.AssetSummary> result = sut.getAssetSummary(
                USER_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).assetName()).isEqualTo("카드");      // 10,000 (최대)
        assertThat(result.get(0).totalAmount()).isEqualTo(10_000L);
        assertThat(result.get(1).assetName()).isEqualTo("통장");      // 8,000
        assertThat(result.get(1).totalAmount()).isEqualTo(8_000L);
        assertThat(result.get(1).count()).isEqualTo(2);
    }
}
