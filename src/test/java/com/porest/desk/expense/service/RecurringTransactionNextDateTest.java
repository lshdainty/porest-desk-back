package com.porest.desk.expense.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.TxKind;
import com.porest.desk.expense.type.RecurringFrequency;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 반복 거래 "다음 실행일" 계산 로직 회귀 방지 테스트.
 * today 의존을 피하려 미래 startDate 를 사용한다(startDate >= today 이면 startDate 기준으로 주기 보정).
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionNextDateTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));
    // 배치 날짜 판정용 — 실제 동작이 필요하므로 mock 대신 실물 주입
    @Spy private ServiceClock serviceClock = new ServiceClock("Asia/Seoul");

    // 배치는 건마다 새 트랜잭션을 연다 — 단위 테스트에서는 상태만 돌려주고 커밋은 no-op.
    @Mock private PlatformTransactionManager transactionManager;

    // 이 테스트는 지출 반복만 태우지만 생성자가 요구한다.
    @Mock private AssetService assetService;
    /**
     * 참조 해석(카테고리·자산)은 <b>진짜</b>를 준다 — 목으로 바꾸면 "남의 카테고리 거절"
     * 같은 단언이 스텁을 확인하는 셈이 되어 아무것도 안 지킨다. 리포지토리 목을 그대로
     * 물리므로 기존 스텁·단언의 뜻이 유지된다.
     */
    private RecurringTransactionServiceImpl sut;

    @BeforeEach
    void buildSut() {
        sut = new RecurringTransactionServiceImpl(
            recurringTransactionRepository, userClock, expenseCategoryRepository,
            assetRepository, expenseRepository, userRepository, balanceHistoryService,
            assetService, serviceClock,
            new ReservationRefs(expenseCategoryRepository, assetRepository),
            transactionManager);
    }

    @BeforeEach
    void givenTransaction() {
        lenient().when(transactionManager.getTransaction(any()))
            .thenReturn(new SimpleTransactionStatus());
    }

    private static final long USER_ID = 1L;
    /** 실제 now 와 무관하게 항상 미래(2개월 뒤 5일)인 시작일. */
    private static final LocalDate FUTURE_START = LocalDate.now().plusMonths(2).withDayOfMonth(5);

    private RecurringTransactionServiceDto.CreateCommand cmd(
            RecurringFrequency freq, Integer dayOfWeek, Integer dayOfMonth, LocalDate startDate) {
        return new RecurringTransactionServiceDto.CreateCommand(
                USER_ID, null, null, null, null, null, null, TxKind.EXPENSE, 10_000L,
                null, null, null, freq, 1, dayOfWeek, dayOfMonth, null, startDate, null, null, null, null);
    }

    private void givenUser() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
    }

    @Test
    @DisplayName("DAILY — 시작일 그대로가 다음 실행일")
    void daily() {
        givenUser();
        var info = sut.createRecurring(cmd(RecurringFrequency.DAILY, null, null, FUTURE_START));
        assertThat(info.nextExecutionDate()).isEqualTo(FUTURE_START);
    }

    @Test
    @DisplayName("WEEKLY — 지정 요일로 (시작일 이후 7일 내) 보정")
    void weekly() {
        givenUser();
        int targetDow = DayOfWeek.MONDAY.getValue(); // 1
        var info = sut.createRecurring(cmd(RecurringFrequency.WEEKLY, targetDow, null, FUTURE_START));

        LocalDate next = info.nextExecutionDate();
        assertThat(next.getDayOfWeek().getValue()).isEqualTo(targetDow);
        assertThat(next).isAfterOrEqualTo(FUTURE_START);
        assertThat(next).isBefore(FUTURE_START.plusDays(7));
    }

    @Test
    @DisplayName("MONTHLY — 지정 일자(15일)로 보정")
    void monthly() {
        givenUser();
        var info = sut.createRecurring(cmd(RecurringFrequency.MONTHLY, null, 15, FUTURE_START));

        LocalDate next = info.nextExecutionDate();
        assertThat(next.getDayOfMonth()).isEqualTo(15);       // 시작일(5일) 이후 같은 달 15일
        assertThat(next.getMonth()).isEqualTo(FUTURE_START.getMonth());
    }

    // ── 실행경로(executeDueTransactions) 다음 실행일·생성 결과 정확성 ───────
    private RecurringTransaction recurring(RecurringFrequency freq, int interval, Integer dayOfMonth,
                                           LocalDate next, Integer maxOccurrences) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        RecurringTransaction r = RecurringTransaction.createRecurring(
                u, null, null, null, null, null, null, TxKind.EXPENSE, 10_000L, null, null, null,
                freq, interval, null, dayOfMonth, null, next, null, maxOccurrences, next, true, true);
        ReflectionTestUtils.setField(r, "rowId", 100L);
        return r;
    }

    private void execute(RecurringTransaction r) {
        // 오늘 = 그 회차 날. 배치는 오늘까지 도래한 회차를 한 번에 따라잡으므로(QA 30 4) 벽시계를
        // 쓰면 2024 년 규칙이 오늘까지 전부 돌아 "한 번 실행한 다음 날짜" 를 볼 수 없다.
        lenient().doReturn(r.getNextExecutionDate()).when(serviceClock).today();
        given(recurringTransactionRepository.findDueTransactions(any())).willReturn(List.of(r));
        // 배치는 건마다 새 트랜잭션에서 다시 읽는다 — 목록에서 들고 나오는 건 rowId 뿐이다.
        given(recurringTransactionRepository.findById(r.getRowId())).willReturn(Optional.of(r));
        sut.executeDueTransactions();
    }

    @Test
    @DisplayName("실행 MONTHLY 31일 → 짧은 달 클램프 (Jan 31 → Feb 28, 비윤년)")
    void executeMonthEndClamp() {
        RecurringTransaction r = recurring(RecurringFrequency.MONTHLY, 1, 31, LocalDate.of(2026, 1, 31), null);
        execute(r);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(r.getExecutedCount()).isEqualTo(1);
        assertThat(r.getIsActive()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("실행 MONTHLY 긴 달에서 dom=31 복원 (Feb 28 → Mar 31)")
    void executeDomRestoreOnLongMonth() {
        RecurringTransaction r = recurring(RecurringFrequency.MONTHLY, 1, 31, LocalDate.of(2026, 2, 28), null);
        execute(r);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("실행 YEARLY 윤일 2/29 → 비윤년 2/28 (2024→2025)")
    void executeYearlyLeapClamp() {
        RecurringTransaction r = recurring(RecurringFrequency.YEARLY, 1, null, LocalDate.of(2024, 2, 29), null);
        execute(r);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    @DisplayName("실행 MONTHLY dom=29 → 윤년 2월은 29 유지 (2024-01-29 → 2024-02-29)")
    void executeLeapFebDom29() {
        RecurringTransaction r = recurring(RecurringFrequency.MONTHLY, 1, 29, LocalDate.of(2024, 1, 29), null);
        execute(r);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("실행 WEEKLY interval=2 → +14일, 요일 보존")
    void executeWeeklyInterval2() {
        RecurringTransaction r = recurring(RecurringFrequency.WEEKLY, 2, null, LocalDate.of(2026, 6, 1), null);
        execute(r);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 6, 15)); // 월요일 보존
    }

    @Test
    @DisplayName("실행 maxOccurrences 도달 시 isActive=N 자동 비활성화")
    void executeMaxOccurrencesDeactivates() {
        RecurringTransaction r = recurring(RecurringFrequency.DAILY, 1, null, LocalDate.of(2026, 6, 10), 3);
        ReflectionTestUtils.setField(r, "executedCount", 2); // 직전 2회 실행 상태
        execute(r);
        assertThat(r.getExecutedCount()).isEqualTo(3);
        assertThat(r.getIsActive()).isEqualTo(YNType.N);
        assertThat(r.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 6, 11));
    }
}
