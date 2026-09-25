package com.porest.desk.expense.service;

import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
import com.porest.desk.expense.type.TxKind;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * 반복 거래의 회차 규칙(QA 30 1·2·4, 2026-09-25).
 *
 * <p>회차는 <b>시작일에서 출발해 주기대로</b> 가는 날짜이고, 거래는 <b>회차 날짜로</b> 찍힌다.
 * <ul>
 *   <li>생성: 매년은 시작일의 월·일을 지킨다 · 격월은 시작일 박자로 · 오늘이 회차면 바로 기록 ·
 *       원거래에서 만든 규칙은 원거래가 그 회차다</li>
 *   <li>수정: 회차를 정하는 칸이 안 바뀌면 다음 회차를 그대로 둔다 · 오늘 이미 기록했으면 오늘은 넘긴다</li>
 *   <li>재개: 멈춘 동안의 회차는 건너뛴다(사용자 결정) · 오늘이 회차면 바로 기록</li>
 *   <li>배치: 밀린 회차를 한 번에, 각자 회차 날짜로 · 종료일 뒤 회차는 안 만든다</li>
 * </ul>
 * 오늘은 2026-09-25(금)로 고정한다 — 벽시계에 물리면 날짜가 지나며 조용히 깨진다.
 */
@ExtendWith(MockitoExtension.class)
class RecurringScheduleRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25); // 금요일
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 10L;

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private AssetService assetService;
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));
    @Spy private ServiceClock serviceClock = new ServiceClock("Asia/Seoul");
    @Mock private PlatformTransactionManager transactionManager;

    private RecurringTransactionServiceImpl sut;
    private User user;
    private ExpenseCategory category;

    @BeforeEach
    void setUp() {
        sut = new RecurringTransactionServiceImpl(
            recurringTransactionRepository, userClock, expenseCategoryRepository,
            assetRepository, expenseRepository, userRepository, balanceHistoryService,
            assetService, serviceClock,
            new ReservationRefs(expenseCategoryRepository, assetRepository),
            transactionManager);
        lenient().doReturn(TODAY).when(serviceClock).today();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        user = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(user, "rowId", USER_ID);
        category = ExpenseCategory.createCategory(user, "구독", "tag", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(category, "rowId", CATEGORY_ID);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        lenient().when(expenseCategoryRepository.hasChildren(CATEGORY_ID)).thenReturn(false);
    }

    private RecurringTransactionServiceDto.CreateCommand create(RecurringFrequency f, Integer interval,
                                                               Integer dow, Integer dom, LocalDate start,
                                                               Long sourceExpenseRowId) {
        return new RecurringTransactionServiceDto.CreateCommand(
            USER_ID, CATEGORY_ID, null, null, null, null, sourceExpenseRowId, TxKind.EXPENSE, 10_000L,
            "넷플릭스", null, null, f, interval, dow, dom, null, start, null, null, null, null);
    }

    private RecurringTransactionServiceDto.UpdateCommand update(RecurringTransaction r, RecurringFrequency f,
                                                               Integer dow, Integer dom, long amount) {
        return new RecurringTransactionServiceDto.UpdateCommand(
            CATEGORY_ID, null, null, null, null, TxKind.EXPENSE, amount,
            r.getDescription(), null, null, f, r.getIntervalValue(), dow, dom, null,
            r.getStartDate(), r.getEndDate(), r.getMaxOccurrences(), null, null);
    }

    /** 저장된 규칙 하나 — 다음 회차·활성은 인자로. */
    private RecurringTransaction rule(RecurringFrequency f, Integer dom, LocalDate start, LocalDate next,
                                      LocalDate end, boolean active) {
        RecurringTransaction r = RecurringTransaction.createRecurring(
            user, category, null, null, null, null, null,
            TxKind.EXPENSE, 10_000L, "넷플릭스", null, null,
            f, 1, null, dom, null, start, end, null, next, true, false);
        ReflectionTestUtils.setField(r, "rowId", 5L);
        if (!active) r.toggleActive();
        lenient().when(recurringTransactionRepository.findById(5L)).thenReturn(Optional.of(r));
        return r;
    }

    private List<LocalDate> savedExpenseDates(int times) {
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        then(expenseRepository).should(times(times)).save(captor.capture());
        return captor.getAllValues().stream().map(e -> e.getExpenseDate().toLocalDate()).toList();
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("매년 — 시작일의 월·일을 지킨다(3/10 시작 → 저장일 9/25 가 아니라 내년 3/10)")
        void yearlyKeepsAnniversary() {
            var info = sut.createRecurring(create(RecurringFrequency.YEARLY, 1, null, null,
                LocalDate.of(2026, 3, 10), null));

            assertThat(info.nextExecutionDate()).isEqualTo(LocalDate.of(2027, 3, 10));
            then(expenseRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("격월 31일 — 시작일 박자로 간다(1/31 → 3/31 → … → 9/30)")
        void everyOtherMonthFollowsStartBeat() {
            var info = sut.createRecurring(create(RecurringFrequency.MONTHLY, 2, null, 31,
                LocalDate.of(2026, 1, 31), null));

            assertThat(info.nextExecutionDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        @DisplayName("첫 회차가 오늘이면 바로 기록한다 — 거래 날짜는 오늘, 다음 회차는 다음 달")
        void recordsTodayRightAway() {
            var info = sut.createRecurring(create(RecurringFrequency.MONTHLY, 1, null, 25, TODAY, null));

            assertThat(savedExpenseDates(1)).containsExactly(TODAY);
            assertThat(info.nextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 25));
            assertThat(info.executedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("오늘 거래에서 만든 규칙 — 원거래가 오늘 회차라 한 번 더 만들지 않는다")
        void sourceExpenseIsTheFirstOccurrence() {
            Expense source = Expense.createExpense(user, category, null, ExpenseType.EXPENSE, 10_000L,
                "넷플릭스", TODAY.atTime(8, 0), null, null, null, null, null, null);
            ReflectionTestUtils.setField(source, "rowId", 77L);
            given(expenseRepository.findById(77L)).willReturn(Optional.of(source));

            var info = sut.createRecurring(create(RecurringFrequency.MONTHLY, 1, null, 25, TODAY, 77L));

            then(expenseRepository).should(never()).save(any());
            assertThat(info.nextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("회차 칸이 그대로면 다음 회차도 그대로 — 멈춘 규칙의 밀린 회차가 사라지지 않는다")
        void keepsNextWhenScheduleUnchanged() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 5, LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 7, 5), null, false);

            sut.updateRecurring(5L, USER_ID, update(r, RecurringFrequency.MONTHLY, null, 5, 12_000L));

            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 7, 5));
            assertThat(r.getAmount()).isEqualTo(12_000L);
            then(expenseRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("오늘 이미 기록한 규칙의 회차를 바꿔도 오늘을 한 번 더 기록하지 않는다")
        void doesNotRepeatTodayAfterExecution() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 25, LocalDate.of(2026, 1, 25),
                LocalDate.of(2026, 10, 25), null, true);
            // 오늘 자정 배치가 이미 돌았다 — lastExecutedAt 은 JVM 기본 시간대 벽시계로 찍힌다.
            LocalDateTime ranAt = TODAY.atStartOfDay(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().plusMinutes(1);
            ReflectionTestUtils.setField(r, "lastExecutedAt", ranAt);

            // 매주 금요일로 바꾼다 — 오늘도 금요일이다.
            sut.updateRecurring(5L, USER_ID, update(r, RecurringFrequency.WEEKLY, 5, null, 10_000L));

            then(expenseRepository).should(never()).save(any());
            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        }
    }

    @Nested
    @DisplayName("재개")
    class Resume {

        @Test
        @DisplayName("멈춘 동안의 회차는 건너뛴다 — 7월·8월·9월 5일은 기록하지 않고 10/5 부터")
        void skipsOccurrencesWhilePaused() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 5, LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 7, 5), null, false);

            sut.toggleActive(5L, USER_ID);

            assertThat(r.getIsActive()).isEqualTo(YNType.Y);
            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            then(expenseRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("다시 켠 날이 회차면 그날 것은 기록한다")
        void recordsTodayWhenTodayIsAnOccurrence() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 25, LocalDate.of(2026, 1, 25),
                LocalDate.of(2026, 8, 25), null, false);

            sut.toggleActive(5L, USER_ID);

            assertThat(savedExpenseDates(1)).containsExactly(TODAY);
            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        }

        @Test
        @DisplayName("멈출 때는 다음 회차를 건드리지 않는다")
        void pausingKeepsNext() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 25, LocalDate.of(2026, 1, 25),
                LocalDate.of(2026, 10, 25), null, true);

            sut.toggleActive(5L, USER_ID);

            assertThat(r.getIsActive()).isEqualTo(YNType.N);
            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        }
    }

    @Nested
    @DisplayName("자정 배치")
    class Batch {

        @Test
        @DisplayName("밀린 회차를 한 번에, 각자 회차 날짜로 기록한다(9/23·9/24·9/25)")
        void catchesUpWithOccurrenceDates() {
            RecurringTransaction r = rule(RecurringFrequency.DAILY, null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 23), null, true);
            given(recurringTransactionRepository.findDueTransactions(TODAY)).willReturn(List.of(r));

            sut.executeDueTransactions();

            assertThat(savedExpenseDates(3)).containsExactly(
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), TODAY);
            assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 9, 26));
        }

        @Test
        @DisplayName("거래 시각은 회차 날짜의 실행 시각이다 — 배치가 돈 날이 아니다")
        void stampsOccurrenceDateAtExecutionTime() {
            RecurringTransaction r = rule(RecurringFrequency.MONTHLY, 24, LocalDate.of(2026, 1, 24),
                LocalDate.of(2026, 9, 24), null, true);
            given(recurringTransactionRepository.findDueTransactions(TODAY)).willReturn(List.of(r));

            sut.executeDueTransactions();

            ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
            then(expenseRepository).should().save(captor.capture());
            assertThat(captor.getValue().getExpenseDate())
                .isEqualTo(LocalDate.of(2026, 9, 24).atTime(LocalTime.of(9, 0)));
        }

        @Test
        @DisplayName("종료일 뒤 회차는 만들지 않는다(종료 9/24 → 9/23·9/24 만)")
        void stopsAtEndDate() {
            RecurringTransaction r = rule(RecurringFrequency.DAILY, null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), true);
            given(recurringTransactionRepository.findDueTransactions(TODAY)).willReturn(List.of(r));

            sut.executeDueTransactions();

            assertThat(savedExpenseDates(2)).containsExactly(
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24));
        }
    }
}
