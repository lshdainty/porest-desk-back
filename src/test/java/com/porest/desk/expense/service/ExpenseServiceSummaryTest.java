package com.porest.desk.expense.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseSplit;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

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
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private ExpenseServiceImpl sut;

    private static final long USER_ID = 1L;

    private Expense expense(ExpenseType type, long amount, String merchant) {
        return Expense.createExpense(null, null, null, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), merchant, "CARD", null, null,
            null,
            null,
            null);
    }

    private ExpenseCategory category(long rowId, String name, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(null, name, "tag", "#fff", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private Expense expenseIn(ExpenseCategory cat, ExpenseType type, long amount) {
        return Expense.createExpense(null, cat, null, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), null, null, null, null,
            null,
            null,
            null);
    }

    private Expense expenseOn(ExpenseCategory cat, ExpenseType type, long amount, LocalDateTime at) {
        return Expense.createExpense(null, cat, null, type, amount, null, at, null, null, null, null,
            null,
            null,
            null);
    }

    private Asset asset(long rowId, String name) {
        Asset a = Asset.createAsset(null, name, AssetType.BANK_ACCOUNT, 0L, "KRW",
            null,
                null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private Expense expenseWithAsset(Asset asset, ExpenseType type, long amount) {
        return Expense.createExpense(null, null, asset, type, amount, null,
                LocalDateTime.of(2026, 6, 15, 12, 0), null, null, null, null,
            null,
            null,
            null);
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
    @DisplayName("getRangeSummary — split 거래는 분할 카테고리별 집계, totalExpense는 부모 raw amount 기준(합 일치)")
    void rangeSummarySplitBreakdown() {
        ExpenseCategory food = category(20L, "식비", null);
        ExpenseCategory coffee = category(31L, "커피", null);
        ExpenseCategory lunch = category(32L, "점심", null);
        Expense e1 = expenseIn(food, ExpenseType.EXPENSE, 30_000L);   // split 2건으로 분할
        ReflectionTestUtils.setField(e1, "rowId", 100L);
        Expense e2 = expenseIn(lunch, ExpenseType.EXPENSE, 10_000L);  // split 없음
        ReflectionTestUtils.setField(e2, "rowId", 101L);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(e1, e2));
        given(expenseSplitRepository.findByExpenseIds(anyList())).willReturn(List.of(
                ExpenseSplit.create(e1, coffee, 12_000L, "커피", 0),
                ExpenseSplit.create(e1, lunch, 18_000L, "점심", 1)
        ));

        ExpenseServiceDto.RangeSummary summary = sut.getRangeSummary(
                USER_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        // totalExpense 는 raw amount 합(분할 무관): 30,000 + 10,000
        assertThat(summary.totalExpense()).isEqualTo(40_000L);
        Map<Long, Long> byId = summary.categoryBreakdown().stream()
                .collect(Collectors.toMap(ExpenseServiceDto.CategoryBreakdown::categoryRowId,
                        ExpenseServiceDto.CategoryBreakdown::totalAmount));
        // 식비(20)는 split 으로 대체되어 등장하지 않음; leaf 키 = {커피31, 점심32}
        assertThat(byId).containsOnlyKeys(31L, 32L);
        assertThat(byId.get(31L)).isEqualTo(12_000L);            // 커피 split
        assertThat(byId.get(32L)).isEqualTo(28_000L);            // 점심 split 18,000 + e2 직접 10,000
        // breakdown 합 == totalExpense (split 합이 부모와 일치하는 정상 케이스)
        assertThat(byId.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("getRangeSummary — 여러 월 혼재 시 빈 달 0 포함 연속 슬롯으로 type별 정확 합산")
    void rangeSummaryMonthlyBuckets() {
        ExpenseCategory food = category(20L, "식비", null);
        given(expenseSplitRepository.findByExpenseIds(anyList())).willReturn(List.of());
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(
                        expenseOn(food, ExpenseType.EXPENSE, 50_000L, LocalDateTime.of(2026, 4, 10, 12, 0)),
                        expenseOn(food, ExpenseType.INCOME, 200_000L, LocalDateTime.of(2026, 4, 25, 12, 0)),
                        expenseOn(food, ExpenseType.EXPENSE, 30_000L, LocalDateTime.of(2026, 6, 5, 12, 0)),
                        expenseOn(food, ExpenseType.EXPENSE, 20_000L, LocalDateTime.of(2026, 6, 20, 12, 0))
                ));   // 5월 거래 없음

        ExpenseServiceDto.RangeSummary summary = sut.getRangeSummary(
                USER_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        assertThat(summary.totalIncome()).isEqualTo(200_000L);
        assertThat(summary.totalExpense()).isEqualTo(100_000L);   // 50,000+30,000+20,000
        List<ExpenseServiceDto.RangeMonthlyBucket> buckets = summary.monthlyBuckets();
        assertThat(buckets).hasSize(3);                            // 4,5,6월 연속 슬롯
        assertThat(buckets.get(0).year()).isEqualTo(2026);
        assertThat(buckets.get(0).month()).isEqualTo(4);
        assertThat(buckets.get(0).totalIncome()).isEqualTo(200_000L);
        assertThat(buckets.get(0).totalExpense()).isEqualTo(50_000L);
        assertThat(buckets.get(1).month()).isEqualTo(5);          // 빈 달
        assertThat(buckets.get(1).totalIncome()).isEqualTo(0L);
        assertThat(buckets.get(1).totalExpense()).isEqualTo(0L);
        assertThat(buckets.get(2).month()).isEqualTo(6);
        assertThat(buckets.get(2).totalIncome()).isEqualTo(0L);
        assertThat(buckets.get(2).totalExpense()).isEqualTo(50_000L);   // 30,000+20,000
    }

    @Test
    @DisplayName("getRangeSummary — 월별 버킷에 split-aware EXPENSE 카테고리 분해 포함(수입 제외, 빈 달은 빈 리스트)")
    void rangeSummaryMonthlyCategoryExpenses() {
        ExpenseCategory food = category(20L, "식비", null);
        ExpenseCategory coffee = category(31L, "커피", null);
        ExpenseCategory lunch = category(32L, "점심", null);
        Expense e1 = expenseOn(food, ExpenseType.EXPENSE, 30_000L, LocalDateTime.of(2026, 4, 10, 12, 0)); // 4월·split
        ReflectionTestUtils.setField(e1, "rowId", 100L);
        Expense e2 = expenseOn(food, ExpenseType.EXPENSE, 20_000L, LocalDateTime.of(2026, 6, 5, 12, 0));  // 6월·직접
        ReflectionTestUtils.setField(e2, "rowId", 101L);
        Expense e3 = expenseOn(food, ExpenseType.INCOME, 200_000L, LocalDateTime.of(2026, 6, 25, 12, 0)); // 6월·수입(제외)
        ReflectionTestUtils.setField(e3, "rowId", 102L);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(e1, e2, e3));
        given(expenseSplitRepository.findByExpenseIds(anyList())).willReturn(List.of(
                ExpenseSplit.create(e1, coffee, 12_000L, "커피", 0),
                ExpenseSplit.create(e1, lunch, 18_000L, "점심", 1)
        ));

        ExpenseServiceDto.RangeSummary summary = sut.getRangeSummary(
                USER_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        List<ExpenseServiceDto.RangeMonthlyBucket> buckets = summary.monthlyBuckets();
        assertThat(buckets).hasSize(3);
        // 4월 — split 분해: 커피 12,000 / 점심 18,000 (부모 식비 미등장)
        Map<Long, Long> apr = buckets.get(0).categoryExpenses().stream()
                .collect(Collectors.toMap(ExpenseServiceDto.CategoryAmount::categoryRowId,
                        ExpenseServiceDto.CategoryAmount::amount));
        assertThat(apr).containsOnlyKeys(31L, 32L);
        assertThat(apr.get(31L)).isEqualTo(12_000L);
        assertThat(apr.get(32L)).isEqualTo(18_000L);
        // 5월 — 빈 달
        assertThat(buckets.get(1).categoryExpenses()).isEmpty();
        // 6월 — 식비 직접 20,000 (수입 200,000 제외)
        Map<Long, Long> jun = buckets.get(2).categoryExpenses().stream()
                .collect(Collectors.toMap(ExpenseServiceDto.CategoryAmount::categoryRowId,
                        ExpenseServiceDto.CategoryAmount::amount));
        assertThat(jun).containsOnlyKeys(20L);
        assertThat(jun.get(20L)).isEqualTo(20_000L);
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
