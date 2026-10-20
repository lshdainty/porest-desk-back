package io.groo.damda.expense.service;

import io.groo.core.exception.ForbiddenException;
import io.groo.core.time.ServiceClock;
import io.groo.core.time.UserClock;
import io.groo.damda.asset.repository.AssetRepository;
import io.groo.damda.asset.service.AssetBalanceHistoryService;
import io.groo.damda.calendar.repository.CalendarEventRepository;
import io.groo.damda.expense.domain.Expense;
import io.groo.damda.expense.domain.ExpenseCategory;
import io.groo.damda.expense.repository.ExpenseBudgetRepository;
import io.groo.damda.expense.repository.ExpenseCategoryRepository;
import io.groo.damda.expense.repository.ExpenseRepository;
import io.groo.damda.expense.repository.ExpenseSplitRepository;
import io.groo.damda.expense.service.dto.ExpenseServiceDto;
import io.groo.damda.expense.type.ExpenseType;
import io.groo.damda.notification.service.NotificationService;
import io.groo.damda.todo.repository.TodoRepository;
import io.groo.damda.user.domain.User;
import io.groo.damda.user.repository.UserRepository;
import io.groo.damda.user.service.UserService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 고정 수입·지출 — 통계가 전체 / 고정 / 변동으로 나눠 보는 재료를 서비스가 맞게 내는지.
 * 실제 Expense 엔티티로 서비스의 집계·표식 로직만 검증한다(레포는 mock).
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceFixedTest {

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
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));
    // 목록 변환이 돈 칸 잠금(D12)을 판정한다 — 회차 결제일 이력이 비면 카드의 지금 결제일 하나로 센다.
    private final io.groo.damda.asset.repository.AssetPaymentDayHistoryRepository paymentDayHistory =
        org.mockito.Mockito.mock(io.groo.damda.asset.repository.AssetPaymentDayHistoryRepository.class);
    @Spy private io.groo.damda.card.service.PaymentScheduleService paymentScheduleService =
        new io.groo.damda.card.service.PaymentScheduleService(paymentDayHistory);
    @Spy private ServiceClock serviceClock = new ServiceClock("Asia/Seoul");

    @InjectMocks private ExpenseServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner, ExpenseType type) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "c" + rowId, "tag", "#fff", type, null);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private Expense tx(long rowId, User u, ExpenseCategory cat, ExpenseType type, long amount,
                       LocalDateTime at, boolean fixed) {
        Expense e = Expense.createExpense(u, cat, null, type, amount, null, at, null, null, null,
            null, null, null);
        ReflectionTestUtils.setField(e, "rowId", rowId);
        e.markFixed(fixed);
        return e;
    }

    @Test
    @DisplayName("getRangeSummary — 합계·월별·카테고리에 고정 몫이 함께 나가고 변동 = 전체 − 고정이다")
    void rangeSummaryCarriesFixedShare() {
        User u = user(USER_ID);
        ExpenseCategory housing = category(10L, u, ExpenseType.EXPENSE);
        ExpenseCategory food = category(11L, u, ExpenseType.EXPENSE);
        ExpenseCategory salary = category(12L, u, ExpenseType.INCOME);
        LocalDateTime june = LocalDateTime.of(2026, 6, 5, 9, 0);
        LocalDateTime july = LocalDateTime.of(2026, 7, 5, 9, 0);

        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        given(expenseRepository.findByDateRange(USER_ID, start, end, null)).willReturn(List.of(
            tx(1L, u, housing, ExpenseType.EXPENSE, 700_000L, june, true),
            tx(2L, u, food, ExpenseType.EXPENSE, 40_000L, june, false),
            tx(3L, u, housing, ExpenseType.EXPENSE, 700_000L, july, true),
            tx(4L, u, food, ExpenseType.EXPENSE, 90_000L, july, false),
            tx(5L, u, salary, ExpenseType.INCOME, 3_000_000L, june, true),
            tx(6L, u, salary, ExpenseType.INCOME, 100_000L, july, false)));
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of());

        var summary = sut.getRangeSummary(USER_ID, start, end);

        assertThat(summary.totalExpense()).isEqualTo(1_530_000L);
        assertThat(summary.fixedExpense()).isEqualTo(1_400_000L);
        assertThat(summary.totalIncome()).isEqualTo(3_100_000L);
        assertThat(summary.fixedIncome()).isEqualTo(3_000_000L);

        // 월별 — 6월·7월 각각 자기 몫만
        var june0 = summary.monthlyBuckets().get(0);
        var july0 = summary.monthlyBuckets().get(1);
        assertThat(june0.totalExpense()).isEqualTo(740_000L);
        assertThat(june0.fixedExpense()).isEqualTo(700_000L);
        assertThat(june0.fixedIncome()).isEqualTo(3_000_000L);
        assertThat(july0.totalExpense()).isEqualTo(790_000L);
        assertThat(july0.fixedExpense()).isEqualTo(700_000L);
        assertThat(july0.fixedIncome()).isZero();

        // 카테고리 — 월세는 전부 고정, 식비는 전부 변동
        var byCat = summary.categoryBreakdown().stream()
            .collect(java.util.stream.Collectors.toMap(
                ExpenseServiceDto.CategoryBreakdown::categoryRowId, b -> b));
        assertThat(byCat.get(10L).totalAmount()).isEqualTo(1_400_000L);
        assertThat(byCat.get(10L).fixedAmount()).isEqualTo(1_400_000L);
        assertThat(byCat.get(11L).totalAmount()).isEqualTo(130_000L);
        assertThat(byCat.get(11L).fixedAmount()).isZero();

        // 월별 카테고리 분해에도 고정 몫이 실린다(고정 제외 보기의 도넛·추이가 이걸 쓴다)
        var housingJune = june0.categoryExpenses().stream()
            .filter(c -> Long.valueOf(10L).equals(c.categoryRowId())).findFirst().orElseThrow();
        assertThat(housingJune.amount()).isEqualTo(700_000L);
        assertThat(housingJune.fixedAmount()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("getRangeSummary — 같은 카테고리에 고정과 변동이 섞이면 고정 몫만 따로 나뉜다")
    void sameCategoryMixedFixedAndVariable() {
        User u = user(USER_ID);
        ExpenseCategory sub = category(20L, u, ExpenseType.EXPENSE);
        LocalDateTime at = LocalDateTime.of(2026, 6, 5, 9, 0);
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        given(expenseRepository.findByDateRange(USER_ID, start, end, null)).willReturn(List.of(
            tx(1L, u, sub, ExpenseType.EXPENSE, 14_900L, at, true),     // 정기 구독
            tx(2L, u, sub, ExpenseType.EXPENSE, 8_000L, at, false)));   // 일회성 결제
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of());

        var b = sut.getRangeSummary(USER_ID, start, end).categoryBreakdown().get(0);

        assertThat(b.totalAmount()).isEqualTo(22_900L);
        assertThat(b.fixedAmount()).isEqualTo(14_900L);
    }

    @Test
    @DisplayName("getRangeSummary — 환불된 고정 거래는 합계에서도 고정 몫에서도 빠진다")
    void refundedFixedExcluded() {
        User u = user(USER_ID);
        ExpenseCategory cat = category(30L, u, ExpenseType.EXPENSE);
        LocalDateTime at = LocalDateTime.of(2026, 6, 5, 9, 0);
        Expense refunded = tx(1L, u, cat, ExpenseType.EXPENSE, 700_000L, at, true);
        refunded.markRefunded(at.plusDays(1));
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        given(expenseRepository.findByDateRange(USER_ID, start, end, null)).willReturn(List.of(
            refunded, tx(2L, u, cat, ExpenseType.EXPENSE, 10_000L, at, false)));
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of());

        var summary = sut.getRangeSummary(USER_ID, start, end);

        assertThat(summary.totalExpense()).isEqualTo(10_000L);
        assertThat(summary.fixedExpense()).isZero();
    }

    @Test
    @DisplayName("getMonthlyTrend — 달마다 고정 수입·지출이 함께 나간다")
    void trendCarriesFixedShare() {
        User u = user(USER_ID);
        ExpenseCategory cat = category(40L, u, ExpenseType.EXPENSE);
        LocalDate today = userClock.today(USER_ID);
        LocalDateTime thisMonth = today.withDayOfMonth(1).atStartOfDay().plusHours(1);
        given(expenseRepository.findByDateRange(eq(USER_ID), any(), any(), any())).willReturn(List.of(
            tx(1L, u, cat, ExpenseType.EXPENSE, 700_000L, thisMonth, true),
            tx(2L, u, cat, ExpenseType.EXPENSE, 30_000L, thisMonth, false)));

        var trends = sut.getMonthlyTrend(USER_ID, 3);
        var last = trends.get(trends.size() - 1);

        // 이번 달 1일 01:00 거래는 오늘 이 시각 이전일 때만 센다(월초 새벽 실행에도 흔들리지 않게 합이 아니라 관계로 본다)
        assertThat(last.fixedExpense()).isLessThanOrEqualTo(last.totalExpense());
        assertThat(last.totalExpense() - last.fixedExpense()).isIn(0L, 30_000L);
    }

    @Test
    @DisplayName("searchExpenses — fixed=true 면 고정만, false 면 변동만, null 이면 전부")
    void searchFiltersByFixed() {
        User u = user(USER_ID);
        ExpenseCategory cat = category(50L, u, ExpenseType.EXPENSE);
        LocalDateTime at = LocalDateTime.of(2026, 6, 5, 9, 0);
        Expense fixed = tx(1L, u, cat, ExpenseType.EXPENSE, 700_000L, at, true);
        Expense variable = tx(2L, u, cat, ExpenseType.EXPENSE, 10_000L, at, false);
        given(expenseRepository.search(eq(USER_ID), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(List.of(fixed, variable));
        given(expenseSplitRepository.findByExpenseIds(any())).willReturn(List.of());

        java.util.function.Function<Boolean, List<Long>> ids = flag -> sut.searchExpenses(
                new ExpenseServiceDto.SearchCommand(USER_ID, null, null, null, null, null, null, null,
                    null, null, flag))
            .stream().map(ExpenseServiceDto.ExpenseInfo::rowId).toList();

        assertThat(ids.apply(true)).containsExactly(1L);
        assertThat(ids.apply(false)).containsExactly(2L);
        assertThat(ids.apply(null)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("ExpenseInfo — 고정 여부가 응답에 실린다")
    void infoExposesFixed() {
        User u = user(USER_ID);
        Expense e = tx(1L, u, category(60L, u, ExpenseType.EXPENSE), ExpenseType.EXPENSE, 1_000L,
            LocalDateTime.of(2026, 6, 5, 9, 0), true);

        assertThat(ExpenseServiceDto.ExpenseInfo.from(e).isFixed()).isTrue();
    }

    @Test
    @DisplayName("bulkMarkFixed — 고른 거래를 한꺼번에 바꾸고 실제로 바뀐 건수만 돌려준다")
    void bulkMarksAndCountsChanged() {
        User u = user(USER_ID);
        ExpenseCategory cat = category(70L, u, ExpenseType.EXPENSE);
        LocalDateTime at = LocalDateTime.of(2026, 6, 5, 9, 0);
        Expense a = tx(1L, u, cat, ExpenseType.EXPENSE, 1_000L, at, false);
        Expense b = tx(2L, u, cat, ExpenseType.EXPENSE, 2_000L, at, true);   // 이미 고정
        given(expenseRepository.findById(1L)).willReturn(Optional.of(a));
        given(expenseRepository.findById(2L)).willReturn(Optional.of(b));

        int changed = sut.bulkMarkFixed(new ExpenseServiceDto.BulkFixedCommand(USER_ID, List.of(1L, 2L, 2L), true));

        assertThat(changed).isEqualTo(1);
        assertThat(a.isFixedTx()).isTrue();
        assertThat(b.isFixedTx()).isTrue();
    }

    @Test
    @DisplayName("bulkMarkFixed — 남의 거래가 하나라도 섞이면 전체를 거절하고 아무것도 바꾸지 않는다")
    void bulkRejectsForeignAndChangesNothing() {
        User me = user(USER_ID);
        User other = user(99L);
        ExpenseCategory cat = category(80L, me, ExpenseType.EXPENSE);
        LocalDateTime at = LocalDateTime.of(2026, 6, 5, 9, 0);
        Expense mine = tx(1L, me, cat, ExpenseType.EXPENSE, 1_000L, at, false);
        Expense theirs = tx(2L, other, cat, ExpenseType.EXPENSE, 2_000L, at, false);
        given(expenseRepository.findById(1L)).willReturn(Optional.of(mine));
        given(expenseRepository.findById(2L)).willReturn(Optional.of(theirs));

        assertThatThrownBy(() -> sut.bulkMarkFixed(
                new ExpenseServiceDto.BulkFixedCommand(USER_ID, List.of(1L, 2L), true)))
            .isInstanceOf(ForbiddenException.class);

        assertThat(mine.isFixedTx()).isFalse();
        assertThat(theirs.isFixedTx()).isFalse();
    }
}
